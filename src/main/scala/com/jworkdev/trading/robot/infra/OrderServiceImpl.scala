package com.jworkdev.trading.robot.infra

import com.jworkdev.trading.robot
import com.jworkdev.trading.robot.{Order, OrderType, OrderTrigger}
import com.jworkdev.trading.robot.service.OrderService
import com.jworkdev.trading.robot.domain.TradingStrategyType
import doobie.*
import doobie.implicits.*
import doobie.implicits.javatimedrivernative.*
import io.github.gaelrenoux.tranzactio.DbException
import io.github.gaelrenoux.tranzactio.doobie.*
import zio.*

import java.time.ZonedDateTime

class OrderServiceImpl extends OrderService:
  private case class OrderDB(
      id: String,
      `type`: String,
      symbol: String,
      dateTime: ZonedDateTime,
      shares: Long,
      price: Double,
      tradingStrategyType: String,
      positionId: Option[Long] = None,
      trigger: String
  ):
    def toDomain: Order = Order(
      id = id,
      `type` = OrderType.valueOf(`type`),
      symbol = symbol,
      dateTime = dateTime,
      shares = shares,
      price = price,
      tradingStrategyType = TradingStrategyType.valueOf(tradingStrategyType),
      positionId = positionId,
      trigger = OrderTrigger.valueOf(trigger)
    )

  private def fromDomain(order: Order): OrderDB =
    OrderDB(
      id = order.id,
      `type` = order.`type`.toString,
      symbol = order.symbol,
      dateTime = order.dateTime,
      shares = order.shares,
      price = order.price,
      tradingStrategyType = order.tradingStrategyType.toString,
      positionId = order.positionId,
      trigger = order.trigger.toString
    )

  override def findAll(): TranzactIO[List[Order]] = tzio{
    sql"""
         SELECT id,
          type,
          symbol,
          date_time,
          shares,
          price,
          trading_strategy_type,
          position_id,
          order_trigger
        FROM trade_order
       """.query[OrderDB]
      .to[List].map(_.map(_.toDomain))
  }

  override def create(orders: List[Order]): TranzactIO[Int] =
    val sql =
      "INSERT INTO trade_order (id,type,symbol,date_time,shares,price,trading_strategy_type,position_id,order_trigger) " +
        "VALUES (?,?,?,?,?,?,?,?,?)"
    val update: Update[OrderDB] = Update[OrderDB](sql)
    tzio {
      update.updateMany(orders.map(fromDomain))
    }.mapError(e => DbException.Wrapped(e))

object OrderService:
  val layer: ULayer[OrderService] = ZLayer.succeed(new OrderServiceImpl)