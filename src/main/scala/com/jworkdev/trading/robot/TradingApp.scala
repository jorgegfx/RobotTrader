package com.jworkdev.trading.robot

import com.jworkdev.trading.robot.domain.Position
import com.jworkdev.trading.robot.infra.{DatabaseConfig, FinInstrumentConfigService, PositionService, PositionServiceLayer}
import doobie.util.log.LogHandler
import io.github.gaelrenoux.tranzactio.doobie.*
import io.github.gaelrenoux.tranzactio.{DbException, ErrorStrategiesRef}
import zio.*
import zio.Console.*
import zio.interop.catz.*

import java.time.Instant

object TradingApp extends zio.ZIOAppDefault:
  implicit val dbContext: DbContext = DbContext(logHandler = LogHandler.jdkLogHandler[Task])
  private val positionService = PositionServiceLayer.layer
  private val finInstrumentConfigService = FinInstrumentConfigService.layer
  type AppEnv = Database & PositionService & FinInstrumentConfigService
  private val appEnv = DatabaseConfig.database ++ positionService ++ finInstrumentConfigService

  override def run: ZIO[ZIOAppArgs & Scope, Any, Any] =
    for {
      _ <- Console.printLine("Starting the app")
      trio <- runTradingLoop().provideLayer(appEnv)
      _ <- Console.printLine(trio.mkString(", "))
    } yield ExitCode(0)

  private def runTradingLoop(): ZIO[AppEnv, DbException, List[Position]] = {
    val queries: ZIO[Connection & AppEnv, DbException, List[Position]] = for {
      finInstrumentConfigService <- ZIO.service[FinInstrumentConfigService]
      finInstrumentConfigs <- finInstrumentConfigService.findAll()
      positionService <- ZIO.service[PositionService]

      _ <- positionService.create(
        Position(
          id = 0,
          symbol = "AAPL",
          numberOfShares = 2,
          openPricePerShare = 100,
          closePricePerShare = None,
          openDate = Instant.now,
          closeDate = None,
          pnl = None
        )
      )
      positions <- positionService.findAll()
    } yield positions

    ZIO.serviceWithZIO[Any] { _ =>
      // if this implicit is not provided, tranzactio will use Conf.dbRecovery instead
      implicit val errorRecovery: ErrorStrategiesRef = DatabaseConfig.alternateDbRecovery
      Database.transactionOrWiden(queries)
    }
  }

