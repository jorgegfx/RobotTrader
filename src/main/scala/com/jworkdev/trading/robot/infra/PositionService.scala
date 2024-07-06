package com.jworkdev.trading.robot.infra

import com.jworkdev.trading.robot.domain.Position
import com.jworkdev.trading.robot.service.PositionService
import com.jworkdev.trading.robot.{Order, OrderType}
import doobie.*
import doobie.implicits.*
import doobie.implicits.legacy.instant.*
import io.github.gaelrenoux.tranzactio.DbException
import io.github.gaelrenoux.tranzactio.doobie.*
import zio.*

import java.time.Instant
import java.util.Date

class PositionServiceImpl extends PositionService:

  override def create(position: Position): TranzactIO[Unit] = tzio {
    sql"""INSERT INTO position (symbol,number_of_shares,open_date,open_price_per_share)
         VALUES (${position.symbol},
         ${position.numberOfShares},
         ${position.openDate},
         ${position.openPricePerShare})""".update.run
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
             pnl FROM position"""
        .query[Position]
        .to[List]
    }

  override def findAllOpen(): TranzactIO[List[Position]] = tzio {
    sql"""SELECT id,
           symbol,
           number_of_shares,
           open_price_per_share,
           close_price_per_share,
           open_date,
           close_date,
           pnl FROM position WHERE close_date is null"""
      .query[Position]
      .to[List]
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
           pnl FROM position WHERE close_date is null AND open_date between ${Date.from(from)} AND ${Date.from(to)}"""
        .query[Position]
        .to[List]
    }

  override def findCloseBetween(from: Instant, to: Instant): TranzactIO[List[Position]] = tzio {
    sql"""SELECT id,
           symbol,
           number_of_shares,
           open_price_per_share,
           close_price_per_share,
           open_date,
           close_date,
           pnl FROM position WHERE close_date is not null AND close_date between ${Date.from(from)} AND ${Date.from(to)}"""
      .query[Position]
      .to[List]
  }

  override def getPnL(from: Instant, to: Instant): TranzactIO[Double] =
    findCloseBetween(from = from, to = to).map(_.flatMap(_.pnl).sum)

  override def closeOpenPositions(openPositions: List[Position], orders: List[Order]): TranzactIO[Int] = {
    val closingPositions = openPositions.flatMap(openPosition=>{
      val closingOrder = orders.find(order=>order.`type`==OrderType.Sell && order.positionId.getOrElse(-1) == openPosition.id)
      closingOrder.map(order=>(openPosition.id,order.totalPrice - openPosition.totalOpenPrice,order.price,order.dateTime))
    })
    val sql = """UPDATE position SET close_date=?, close_price_per_share=?, pnl=? WHERE id=?"""
    val params: List[(Instant,Double,Double,Long)] = closingPositions.
        map { case (id: Long, pnl: Double, closePrice: Double, dateTime: Instant) => (dateTime,closePrice,pnl,id) }
    val update: Update[(Instant,Double,Double,Long)] = Update[(Instant,Double,Double,Long)](sql)
    tzio {
      update.updateMany(params)
    }.mapError(e => DbException.Wrapped(e))
  }

  def createOpenPositionsFromOrders(orders: List[Order]): TranzactIO[Int]={
    val positionsToOpen = orders.filter(order=> order.`type` == OrderType.Buy).
          map(order=>(order.symbol,order.shares,order.price,order.dateTime))
    val sql = "INSERT INTO position (symbol,number_of_shares,open_price_per_share,open_date) VALUES (?,?,?,?)"
    val update: Update[(String,Long, Double, Instant)] = Update[(String,Long, Double, Instant)](sql)
    tzio {
      update.updateMany(positionsToOpen)
    }.mapError(e => DbException.Wrapped(e))
  }


object PositionService:
  val layer: ULayer[PositionService] = ZLayer.succeed(new PositionServiceImpl)
