package com.jworkdev.trading.robot

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
      _ <- screeningApp().provide(
        DatabaseConfig.database ++
        StockScreeningService.layer,
        FinInstrumentService.layer,
        ExchangeDataProvider.layer
      )
    yield ()

  /** Main code for the application. Results in a big ZIO depending on the AppEnv. */
  private def screeningApp(): ZIO[AppEnv, Throwable, Unit] =
    val executeTradingScreening: ZIO[Connection & AppEnv, Throwable, Unit] =
      for
        stockScreeningService <- ZIO.service[StockScreeningService]
        finInstruments <- stockScreeningService.screenFinInstruments()
        finInstrumentService <- ZIO.service[FinInstrumentService]
        _ <- Console.printLine(s"Saving ${finInstruments.size} finInstruments ...")
        _ <- finInstrumentService.deleteAll()
        _ <- finInstrumentService.saveAll(finInstruments = finInstruments)
      yield ()

    ZIO.serviceWithZIO[Any] { conf =>
      // if this implicit is not provided, tranzactio will use Conf.dbRecovery instead
      implicit val errorRecovery: ErrorStrategiesRef =
        DatabaseConfig.alternateDbRecovery
      Database.transactionOrWiden(executeTradingScreening)
    }
