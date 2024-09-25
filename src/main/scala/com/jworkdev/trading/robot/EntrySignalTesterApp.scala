package com.jworkdev.trading.robot

import com.jworkdev.trading.robot.config.appConfig
import com.jworkdev.trading.robot.domain.{Account, FinInstrument, Position, TradingExchange}
import com.jworkdev.trading.robot.infra.*
import com.jworkdev.trading.robot.service.{AccountService, FinInstrumentService, PositionService, TradingExchangeService, TradingStrategyService, *}
import com.typesafe.scalalogging.Logger
import doobie.util.log.LogHandler
import io.github.gaelrenoux.tranzactio.ErrorStrategiesRef
import io.github.gaelrenoux.tranzactio.doobie.*
import zio.*
import zio.Console.*
import zio.interop.catz.*

import java.time.ZonedDateTime

object EntrySignalTesterApp extends zio.ZIOAppDefault:
  implicit val dbContext: DbContext =
    DbContext(logHandler = LogHandler.jdkLogHandler[Task])
  type AppEnv = Database & AccountService & PositionService & FinInstrumentService & TradingStrategyService &
    TradingExchangeService & OrderService
  private val accountService = AccountService.layer
  private val positionService = PositionService.layer
  private val tradingStrategyService = TradingStrategyService.layer
  private val finInstrumentService = FinInstrumentService.layer
  private val tradingExchangeService = TradingExchangeService.layer
  private val orderService = OrderService.layer
  private val tradingExecutorService = TradingExecutorService()
  private val appEnv =
    DatabaseConfig.database ++ accountService ++
      positionService ++ finInstrumentService ++
      tradingStrategyService ++ tradingExchangeService ++
      orderService

  implicit val errorRecovery: ErrorStrategiesRef =
    DatabaseConfig.alternateDbRecovery
  private val logger = Logger(classOf[OrderFactoryImpl])
  
  private val executeSignalEntryTesterTransaction: ZIO[Connection & AppEnv, Throwable, Unit] = for
      screenCount <- appConfig.map(_.screenCount)
      finInstruments <- ZIO.serviceWithZIO[FinInstrumentService](_.findTopToTrade(limit = screenCount))
      _ <- ZIO.attempt(logger.info(s"Searching signals on ${finInstruments.map(_.symbol)}"))
    yield ()
  
  private def runTx(): ZIO[AppEnv, Throwable, Unit] =
    Database.transactionOrWiden(executeSignalEntryTesterTransaction)
  
  override def run: ZIO[ZIOAppArgs & Scope, Any, Any] = for
      _ <- runTx().provideLayer(appEnv).foldCauseZIO(cause => ZIO.logErrorCause("Error", cause), _ => ZIO.unit)
    yield()