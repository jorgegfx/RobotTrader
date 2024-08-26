package com.jworkdev.trading.robot.infra

import com.jworkdev.trading.robot.Order
import com.jworkdev.trading.robot.OrderTrigger.Signal
import com.jworkdev.trading.robot.OrderType.{Buy, Sell}
import com.jworkdev.trading.robot.domain.{Position, TradingStrategyType}
import com.jworkdev.trading.robot.service.PositionService
import io.github.gaelrenoux.tranzactio.doobie.*
import io.github.gaelrenoux.tranzactio.{DatabaseOps, ErrorStrategiesRef}
import zio.*
import zio.interop.catz.*
import zio.test.{ZIOSpecDefault, test, *}

import java.time.ZonedDateTime

object PositionServiceSpec extends ZIOSpecDefault:

  private val testPosition = Position(
    id = 0,
    symbol = "NVDA",
    numberOfShares = 2,
    openPricePerShare = 10,
    closePricePerShare = None,
    openDate = ZonedDateTime.now(),
    closeDate = None,
    pnl = None,
    tradingStrategyType = TradingStrategyType.MACD
  )

  private val openPosition = testPosition.copy(symbol = "IBM1")

  val positionServiceLayer: ULayer[PositionService] =
    ZLayer.succeed(new PositionServiceImpl)

  val positionServiceDllLayer: ULayer[PositionServiceDDL] =
    ZLayer.succeed(new PositionServiceDDLImpl)

  private val appEnv =
    H2Database.database ++ positionServiceLayer ++ positionServiceDllLayer

  type AppEnv = Database & PositionService & PositionServiceDDL

  override def spec: Spec[Any, Throwable] = suite("testExecute")(
    test("findAll") {
      val createAndGetPosition = for
        _ <- ZIO.serviceWithZIO[PositionServiceDDL](_.initialize())
        _ <- ZIO.serviceWithZIO[PositionService](_.create(position = testPosition))
        positions <- ZIO.serviceWithZIO[PositionService](_.findAll())
      yield positions.last
      for position <- Database.transactionOrWiden(createAndGetPosition).provideLayer(appEnv)
      yield assertTrue(
        position.symbol == testPosition.symbol &&
          position.numberOfShares == testPosition.numberOfShares
      )
    },
    test("findAllOpen") {
      val createAndGetPosition = for
        _ <- ZIO.serviceWithZIO[PositionServiceDDL](_.initialize())
        _ <- ZIO.serviceWithZIO[PositionService](_.create(position = testPosition))
        positions <- ZIO.serviceWithZIO[PositionService](_.findAllOpen())
      yield positions.last
      for position <- Database.transactionOrWiden(createAndGetPosition).provideLayer(appEnv)
      yield assertTrue(
        position.symbol == testPosition.symbol &&
          position.numberOfShares == testPosition.numberOfShares
      )
    },
    test("createOpenPositionsFromOrders") {
      val createOpenPositionsFromOrders = for
        _ <- ZIO.serviceWithZIO[PositionServiceDDL](_.initialize())
        _ <- ZIO.serviceWithZIO[PositionService](_.createOpenPositionsFromOrders(orders = List(
          Order(
            `type` = Buy,
            symbol = "IBM2",
            dateTime = ZonedDateTime.now(),
            shares = 2,
            price = 20,
            tradingStrategyType = TradingStrategyType.MACD,
            positionId = None,
            trigger = Signal
          )
        )))
        positions <- ZIO.serviceWithZIO[PositionService](_.findAllOpen())
      yield positions
      for position <- Database.transactionOrWiden(createOpenPositionsFromOrders).provideLayer(appEnv)
        yield assertTrue(
          position.exists(position=>position.symbol == "IBM2")
        )
    },
    test("closeOpenPositions") {
      val createAndClosePosition = for
        _ <- ZIO.serviceWithZIO[PositionServiceDDL](_.initialize())
        positionService <- ZIO.service[PositionService]
        _ <- positionService.create(position = openPosition)
        positions <- positionService.findAll()
        count <- positionService.closeOpenPositions(
          openPositions = positions,
          orders = List(
            Order(
              `type` = Sell,
              symbol = openPosition.symbol,
              dateTime = ZonedDateTime.now(),
              shares = 2,
              price = 20,
              tradingStrategyType = TradingStrategyType.MACD,
              positionId = positions.find(position=>openPosition.symbol == position.symbol).map(_.id),
              trigger = Signal
            )
          )
        )
      yield count
      for count <- Database.transactionOrWiden(createAndClosePosition).provideLayer(appEnv)
      yield assertTrue(
        count == 1
      )
    }
  )
