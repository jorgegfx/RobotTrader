package com.jworkdev.trading.robot

import com.jworkdev.trading.robot.config.appConfig
import com.jworkdev.trading.robot.infra.{DatabaseConfig, FinInstrumentService}
import com.jworkdev.trading.robot.market.data.ExchangeDataProvider
import com.jworkdev.trading.robot.service.{FinInstrumentService, StockScreeningService}
import doobie.util.log.LogHandler
import io.github.gaelrenoux.tranzactio.ErrorStrategiesRef
import io.github.gaelrenoux.tranzactio.doobie.*
import zio.*
import zio.interop.catz.*

object TradingScreenerApp extends zio.ZIOAppDefault:
  implicit val dbContext: DbContext =
    DbContext(logHandler = LogHandler.jdkLogHandler[Task])
  type AppEnv = Database & StockScreeningService & FinInstrumentService
  private val stockScreeningService = StockScreeningService.layer
  private val finInstrumentService = FinInstrumentService.layer
  private val exchangeDataProvider = ExchangeDataProvider.layer

  override def run: ZIO[ZIOAppArgs & Scope, Any, Unit] =
    for
      _ <- calculateStatsLoop().provide(
        DatabaseConfig.database ++
          StockScreeningService.layer,
        FinInstrumentService.layer,
        ExchangeDataProvider.layer
      )
    yield ()

  private def calculateStatsLoop(): ZIO[AppEnv, Throwable, Unit] =
    calculateStats().flatMap { res =>
      if (!res) {
        for
          _ <- Console.printLine(s"Next batch ...")
          _ <- ZIO.sleep(Duration.fromSeconds(30))
          _ <- calculateStatsLoop()
        yield ()
      } else {
        ZIO.succeed(())
      }
    }  

  /** Main code for the application. Results in a big ZIO depending on the AppEnv. */
  private def calculateStats(): ZIO[AppEnv, Throwable, Boolean] =
    val executeTradingScreening: ZIO[Connection & AppEnv, Throwable, Boolean] =
      for
        finInstrumentService <- ZIO.service[FinInstrumentService]
        stockScreeningService <- ZIO.service[StockScreeningService]
        screenCount <- appConfig.map(_.screenCount)
        finInstruments <- finInstrumentService.findWithExpiredStats()
        _ <- ZIO.foreach(finInstruments){ finInstrument =>
          for
            _ <- Console.printLine(s"Updating ${finInstrument.symbol} ...")
          yield ()
        }
        stats <- stockScreeningService.calculateStats(symbols = finInstruments.map(_.symbol).toSet)
        - <- finInstrumentService.updateStats(stats = stats)
      yield finInstruments.isEmpty

    ZIO.serviceWithZIO[Any] { conf =>
      // if this implicit is not provided, tranzactio will use Conf.dbRecovery instead
      implicit val errorRecovery: ErrorStrategiesRef =
        DatabaseConfig.alternateDbRecovery
      Database.transactionOrWiden(executeTradingScreening)
    }

