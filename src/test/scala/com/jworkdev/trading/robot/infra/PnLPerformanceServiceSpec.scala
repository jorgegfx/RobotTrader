package com.jworkdev.trading.robot.infra

import com.jworkdev.trading.robot.domain.TradingStrategyType.OpenGap
import com.jworkdev.trading.robot.service.PnLPerformanceService
import io.github.gaelrenoux.tranzactio.doobie.*
import io.github.gaelrenoux.tranzactio.{DatabaseOps, ErrorStrategiesRef}
import zio.*
import zio.interop.catz.*
import zio.test.{ZIOSpecDefault, test, *}

import java.time.LocalDate

object PnLPerformanceServiceSpec extends ZIOSpecDefault:
  val pnLPerformanceServiceLayer: ULayer[PnLPerformanceService] =
    ZLayer.succeed(new PnLPerformanceServiceImpl)

  val pnLPerformanceServiceDDLLayer: ULayer[PnLPerformanceServiceDDL] =
    ZLayer.succeed(new PnLPerformanceServiceDDLImpl)

  private val appEnv =
    H2Database.database ++ pnLPerformanceServiceLayer ++ pnLPerformanceServiceDDLLayer
  type AppEnv = Database & PnLPerformanceService & PnLPerformanceServiceDDL

  override def spec: Spec[Any, Throwable] = suite("testExecute")(
    test("findByEntryDate") {
      val localDate = LocalDate.now()
      val createAndGetPnL =  for {
        _ <- ZIO.serviceWithZIO[PnLPerformanceServiceDDL](_.initialize())
        _ <- ZIO.serviceWithZIO[PnLPerformanceService](_.createOrUpdate(
            entryDate = localDate,
            tradingStrategyType = OpenGap,
            amount = 10))
        pnl <- ZIO.serviceWithZIO[PnLPerformanceService](_.findByEntryDate(
          entryDate = localDate,
          tradingStrategyType = OpenGap))
      } yield pnl
      for
          pnl <- Database.transactionOrWiden(createAndGetPnL).provideLayer(appEnv)
      yield assertTrue(
          pnl.isDefined
      )
    }
  )

