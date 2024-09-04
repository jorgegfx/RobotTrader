package com.jworkdev.trading.robot.infra

import com.jworkdev.trading.robot.OrderType.Buy
import com.jworkdev.trading.robot.domain.TradingStrategyType
import com.jworkdev.trading.robot.{Order, OrderTrigger}
import com.jworkdev.trading.robot.service.OrderService
import io.github.gaelrenoux.tranzactio.doobie.*
import io.github.gaelrenoux.tranzactio.{DatabaseOps, ErrorStrategiesRef}
import zio.*
import zio.interop.catz.*
import zio.test.{ZIOSpecDefault, test, *}

import java.time.ZonedDateTime


object OrderServiceSpec extends ZIOSpecDefault:
  val orderServiceLayer: ULayer[OrderService] =
    ZLayer.succeed(new OrderServiceImpl)

  val orderServiceDllLayer: ULayer[OrderServiceDDL] =
    ZLayer.succeed(new OrderServiceDDLImpl)

  private val appEnv =
    H2Database.database ++ orderServiceLayer ++ orderServiceDllLayer

  type AppEnv = Database & OrderService & OrderServiceDDL
  private val testOrder = Order(
    `type` = Buy,
    symbol = "TEST",
    dateTime = ZonedDateTime.now(),
    shares = 100,
    price = 10,
    tradingStrategyType = TradingStrategyType.OpenGap,
    positionId = None,
    trigger = OrderTrigger.Signal
  )
  override def spec: Spec[Any, Throwable] = suite("testExecute")(
    test("findAll") {
      val createAndGetPosition = for
        _ <- ZIO.serviceWithZIO[OrderServiceDDL](_.initialize())
        _ <- ZIO.serviceWithZIO[OrderService](_.create(orders = List(testOrder)))
        orders <- ZIO.serviceWithZIO[OrderService](_.findAll())
      yield orders.last
      for order <- Database.transactionOrWiden(createAndGetPosition).provideLayer(appEnv)
        yield assertTrue(
          order.symbol == testOrder.symbol &&
            order.shares == testOrder.shares
        )
    })