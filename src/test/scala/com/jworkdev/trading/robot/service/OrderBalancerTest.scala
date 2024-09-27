package com.jworkdev.trading.robot.service

import com.jworkdev.trading.robot.Order
import com.jworkdev.trading.robot.OrderTrigger.Signal
import com.jworkdev.trading.robot.OrderType.{Buy, Sell}
import com.jworkdev.trading.robot.domain.TradingStrategyType.OpenGap
import org.scalatest.funsuite.AnyFunSuiteLike

import java.time.ZonedDateTime

class OrderBalancerTest extends AnyFunSuiteLike:
  private val orderBalancer = OrderBalancer()
  test("testBalanceEmpty") {
    val res = orderBalancer.balance(300, orders = List())
    assert(res.isEmpty)
  }
  test("testBalanceOne") {
    val res = orderBalancer.balance(
      300,
      orders = List(
        Order(
          id = "id",
          `type` = Buy,
          symbol = "SSS",
          dateTime = ZonedDateTime.now(),
          shares = 1,
          price = 10,
          tradingStrategyType = OpenGap,
          positionId = None,
          trigger = Signal
        )
      )
    )
    assert(res.nonEmpty)
    assert(res.head.shares==30)
  }
  test("testBalanceOneBuyOneSell") {
    val res = orderBalancer.balance(
      300,
      orders = List(
        Order(
          id = "id",
          `type` = Buy,
          symbol = "SSS",
          dateTime = ZonedDateTime.now(),
          shares = 1,
          price = 10,
          tradingStrategyType = OpenGap,
          positionId = None,
          trigger = Signal
        ),
        Order(
          id = "id",
          `type` = Sell,
          symbol = "SSA",
          dateTime = ZonedDateTime.now(),
          shares = 1,
          price = 10,
          tradingStrategyType = OpenGap,
          positionId = None,
          trigger = Signal
        )
      )
    )
    assert(res.nonEmpty)
    val buyOrder = res.filter{order=>order.`type`==Buy}.head
    assert(buyOrder.shares == 30)
  }

  test("testBalanceTwoSell") {
    val res = orderBalancer.balance(
      300,
      orders = List(
        Order(
          id = "id",
          `type` = Sell,
          symbol = "SSS",
          dateTime = ZonedDateTime.now(),
          shares = 1,
          price = 10,
          tradingStrategyType = OpenGap,
          positionId = None,
          trigger = Signal
        ),
        Order(
          id = "id",
          `type` = Sell,
          symbol = "SSA",
          dateTime = ZonedDateTime.now(),
          shares = 1,
          price = 10,
          tradingStrategyType = OpenGap,
          positionId = None,
          trigger = Signal
        )
      )
    )
    assert(res.nonEmpty)
    assert(res.filter(order=>order.`type`==Sell).head.shares == 1)
  }
