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
  private val exchange = "NASDAQ"

  override def run: ZIO[ZIOAppArgs & Scope, Any, Unit] =
    for
      _ <- calculateVolatilityLoop().provide(
        DatabaseConfig.database ++
        StockScreeningService.layer,
        FinInstrumentService.layer,
        ExchangeDataProvider.layer
      )
    yield ()

  private def calculateVolatilityLoop(): ZIO[AppEnv, Throwable, Unit] =
    calculateVolatility().flatMap{ res =>
        if(!res){
          for
            _ <- Console.printLine(s"Next batch ...")
            _ <- ZIO.sleep(Duration.fromSeconds(30))
            _ <- calculateVolatilityLoop()
          yield ()
        }else{
          ZIO.succeed(())
        }
    }

  /** Main code for the application. Results in a big ZIO depending on the AppEnv. */
  private def calculateVolatility(): ZIO[AppEnv, Throwable, Boolean] =
    val executeTradingScreening: ZIO[Connection & AppEnv, Throwable, Boolean] =
      for
        finInstrumentService <- ZIO.service[FinInstrumentService]
        stockScreeningService <- ZIO.service[StockScreeningService]
        finInstruments <- finInstrumentService.findWithoutVolatility()
        _ <- Console.printLine(s"Saving ${finInstruments.size} finInstruments ...")
        volatilityMap <- stockScreeningService.calculateVolatility(finInstruments = finInstruments)
        - <- finInstrumentService.updateVolatility(volatilityMap = volatilityMap)
      yield finInstruments.isEmpty

    ZIO.serviceWithZIO[Any] { conf =>
      // if this implicit is not provided, tranzactio will use Conf.dbRecovery instead
      implicit val errorRecovery: ErrorStrategiesRef =
        DatabaseConfig.alternateDbRecovery
      Database.transactionOrWiden(executeTradingScreening)
    }
