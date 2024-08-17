package com.jworkdev.trading.robot.infra

import com.jworkdev.trading.robot.domain.{Position, TradingStrategyType}
import com.jworkdev.trading.robot.service.PositionService
import com.jworkdev.trading.robot.{Order, OrderType}
import doobie.*
import doobie.implicits.*
import doobie.implicits.javatimedrivernative.*
import io.github.gaelrenoux.tranzactio.DbException
import io.github.gaelrenoux.tranzactio.doobie.*
import zio.*

import java.time.{Instant, ZonedDateTime}
import java.util.Date

class PositionServiceImpl extends PositionService:

  override def create(position: Position): TranzactIO[Unit] = tzio {
    sql"""INSERT INTO position (symbol,number_of_shares,open_date,open_price_per_share,tradingStrategyType)
         VALUES (${position.symbol},
         ${position.numberOfShares},
         ${position.openDate},
         ${position.openPricePerShare},
         ${position.tradingStrategyType.toString})""".update.run
      .map(_ => ())
  }

  override def findAll(): TranzactIO[List[Position]] =
    tzio {
      sql"""SELECT id,
             symbol,
             number_of_shares,
             open_price_per_share,
             close_price_per_share,
             open_date,
             close_date,
             pnl,
             trading_strategy_type FROM position"""
        .query[PositionDB]
        .to[List].map(_.map(_.toDomain))
    }

  override def findAllOpen(): TranzactIO[List[Position]] = tzio {
    sql"""SELECT id,
           symbol,
           number_of_shares,
           open_price_per_share,
           close_price_per_share,
           open_date,
           close_date,
           pnl,
           trading_strategy_type FROM position WHERE close_date is null"""
      .query[PositionDB]
      .to[List].map(_.map(_.toDomain))
  }

  override def findOpenBetween(from: Instant, to: Instant): TranzactIO[List[Position]] =
    tzio {
      sql"""SELECT id,
           symbol,
           number_of_shares,
           open_price_per_share,
           close_price_per_share,
           open_date,
           close_date,
           pnl,
           trading_strategy_type FROM position WHERE close_date is null AND open_date between ${Date.from(from)} AND ${Date.from(to)}"""
        .query[PositionDB]
        .to[List].map(_.map(_.toDomain))
    }

  override def getPnL(from: Instant, to: Instant): TranzactIO[Double] =
    findCloseBetween(from = from, to = to).map(_.flatMap(_.pnl).sum)

  override def findCloseBetween(from: Instant, to: Instant): TranzactIO[List[Position]] = tzio {
    sql"""SELECT id,
           symbol,
           number_of_shares,
           open_price_per_share,
           close_price_per_share,
           open_date,
           close_date,
           pnl,
           trading_strategy_type FROM position WHERE close_date is not null AND close_date between ${Date.from(from)} AND ${Date.from(to)}"""
      .query[PositionDB]
      .to[List].map(_.map(_.toDomain))
  }

  override def closeOpenPositions(openPositions: List[Position], orders: List[Order]): TranzactIO[Int] = {
    val closingPositions = openPositions.flatMap(openPosition=>{
      val closingOrder = orders.find(order=>order.`type`==OrderType.Sell && order.positionId.getOrElse(-1) == openPosition.id)
      closingOrder.map(order=>(openPosition.id,order.totalPrice - openPosition.totalOpenPrice,order.price,order.dateTime))
    })
    val sql = """UPDATE position SET close_date=?, close_price_per_share=?, pnl=? WHERE id=?"""
    val params: List[(ZonedDateTime,Double,Double,Long)] = closingPositions.
        map { case (id: Long, pnl: Double, closePrice: Double, dateTime: ZonedDateTime) => (dateTime,closePrice,pnl,id) }
    val update: Update[(ZonedDateTime,Double,Double,Long)] = Update[(ZonedDateTime,Double,Double,Long)](sql)
    tzio {
      update.updateMany(params)
    }.mapError(e => DbException.Wrapped(e))
  }

  def createOpenPositionsFromOrders(orders: List[Order]): TranzactIO[Int]={
    val positionsToOpen = orders.filter(order=> order.`type` == OrderType.Buy).
          map(order=>(order.symbol,order.shares,order.price,order.dateTime,order.tradingStrategyType.toString))
    val sql = "INSERT INTO position (symbol,number_of_shares,open_price_per_share,open_date,trading_strategy_type) VALUES (?,?,?,?,?)"
    val update: Update[(String,Long, Double, ZonedDateTime,String)] = Update[(String,Long, Double, ZonedDateTime,String)](sql)
    tzio {
      update.updateMany(positionsToOpen)
    }.mapError(e => DbException.Wrapped(e))
  }

  private case class PositionDB(
                       id: Long,
                       symbol: String,
                       numberOfShares: Long,
                       openPricePerShare: Double,
                       closePricePerShare: Option[Double],
                       openDate: ZonedDateTime,
                       closeDate: Option[ZonedDateTime],
                       pnl: Option[Double],
                       tradingStrategyType: String
                     ){
    def toDomain: Position = Position(id = id,
      symbol = symbol,
      numberOfShares = numberOfShares,
      openPricePerShare = openPricePerShare,
      closePricePerShare = closePricePerShare,
      openDate = openDate,
      closeDate = closeDate,
      pnl = pnl,
      tradingStrategyType = TradingStrategyType.valueOf(tradingStrategyType))
  }


object PositionService:
  val layer: ULayer[PositionService] = ZLayer.succeed(new PositionServiceImpl)
