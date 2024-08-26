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

object TradingApp extends zio.ZIOAppDefault:
  implicit val dbContext: DbContext =
    DbContext(logHandler = LogHandler.jdkLogHandler[Task])
  type AppEnv = Database & AccountService & PositionService & FinInstrumentService & TradingStrategyService &
    TradingExchangeService
  private val accountService = AccountService.layer
  private val positionService = PositionService.layer
  private val tradingStrategyService = TradingStrategyService.layer
  private val finInstrumentService = FinInstrumentService.layer
  private val tradingExchangeService = TradingExchangeService.layer
  private val tradingExecutorService = TradingExecutorService()
  private val appEnv =
    DatabaseConfig.database ++ accountService ++ positionService ++ finInstrumentService ++ tradingStrategyService ++
      tradingExchangeService
  // Define the interval in minutes
  private val intervalMinutes: Int = 1
  private val schedule: Schedule[Any, Any, Long] = Schedule.fixed(intervalMinutes.minutes)
  private val logger = Logger(classOf[OrderFactoryImpl])
  // Task to be executed periodically
  private val periodicTask: ZIO[AppEnv, Throwable, Unit] = for
    _ <- ZIO.attempt(logger.info(s"Starting ..."))
    currentTime <- Clock.currentDateTime
    _ <- runTradingLoop().foldCauseZIO(cause => ZIO.logErrorCause("Error", cause), _ => ZIO.unit)
    _ <- ZIO.attempt(logger.info(s"Task executed at: $currentTime"))
  yield ()

  private def fetchAllFinInstrument(
      finInstruments: List[FinInstrument],
      openPositions: List[Position]
  ): ZIO[Connection & AppEnv, Throwable, List[FinInstrument]] =
    val symbols = finInstruments.map(_.symbol)
    val missingSymbols = openPositions
      .filter(position => !symbols.contains(position.symbol))
      .map(_.symbol)
    for
      finInstrumentService <- ZIO.service[FinInstrumentService]
      missingFinInstruments <- ZIO.foreach(missingSymbols) { symbol =>
        finInstrumentService.findBySymbol(symbol = symbol)
      }
    yield missingFinInstruments.flatten ++ finInstruments

  private def buildFinInstrumentMap(
      screenCount: Int,
      openPositions: List[Position]
  ): ZIO[Connection & AppEnv, Throwable, Map[FinInstrument, List[Position]]] =
    for
      finInstrumentService <- ZIO.service[FinInstrumentService]
      finInstruments <- finInstrumentService.findTopToTrade(limit = screenCount)
      allFinInstruments <- fetchAllFinInstrument(finInstruments = finInstruments, openPositions = openPositions)
    yield allFinInstruments
      .map(finInstrument => (finInstrument, openPositions.filter(position => position.symbol == finInstrument.symbol)))
      .toMap

  private val executeTradingTransaction: ZIO[Connection & AppEnv, Throwable, Unit] = for
    strategyCfgs <- appConfig.map(_.strategyConfigurations)
    stopLossPercentage <- appConfig.map(_.stopLossPercentage)
    screenCount <- appConfig.map(_.screenCount)
    tradingMode <- appConfig.map(_.tradingMode)
    accountService <- ZIO.service[AccountService]
    account <- accountService.findByName("trading")
    _ <- ZIO.attempt(logger.info(s"Trading with account name :'${account.name}''"))
    positionService <- ZIO.service[PositionService]
    openPositions <- positionService.findAllOpen()
    _ <- ZIO.attempt(logger.info(s"openPositions : $openPositions"))
    finInstrumentService <- ZIO.service[FinInstrumentService]
    finInstruments <- finInstrumentService.findTopToTrade(limit = screenCount)
    _ <- ZIO.attempt(logger.info(s"Searching signals on ${finInstruments.map(_.symbol)}"))
    balancePerFinInst <- ZIO.attempt(
      getBalancePerFinInst(
        account = account,
        finInstruments = finInstruments
      )
    )
    _ <- ZIO.attempt(logger.info(s"balancePerFinInst : $balancePerFinInst"))
    strategyService <- ZIO.service[TradingStrategyService]
    tradingStrategies <- strategyService.findAll()
    _ <- ZIO.attempt(logger.info(s"tradingStrategies : $tradingStrategies"))
    exchangeMap <- getTradingExchangeMap(openPositions = openPositions)
    _ <- ZIO.attempt(logger.info(s"exchangeMap : $exchangeMap"))
    _ <- ZIO.attempt(logger.info("Executing orders ..."))
    finInstrumentMap <- buildFinInstrumentMap(screenCount = screenCount, openPositions = openPositions)
    orders <- tradingExecutorService.execute(
      TradingExecutorRequest(
        balancePerFinInst = balancePerFinInst,
        finInstrumentMap = finInstrumentMap,
        tradingStrategies = tradingStrategies,
        exchangeMap = exchangeMap,
        strategyConfigurations = strategyCfgs,
        stopLossPercentage = stopLossPercentage,
        tradingMode = tradingMode,
        tradingDateTime = ZonedDateTime.now()
      )
    )
    _ <- ZIO.attempt(logger.info(s"Orders created :$orders ..."))
    _ <- applyOrders(
      account = account,
      openPositions = openPositions,
      orders = orders
    )
  yield ()

  override def run: ZIO[ZIOAppArgs & Scope, Any, Any] =
    periodicTask.repeat(schedule).provideLayer(appEnv).exitCode

  private def applyOrders(
      account: Account,
      openPositions: List[Position],
      orders: List[Order]
  ): ZIO[Connection & AppEnv, Throwable, Option[Unit]] =
    ZIO.when(orders.nonEmpty)(ZIO.scoped {
      for
        _ <- ZIO.attempt(logger.info(s"Applying orders :$orders ..."))
        _ <- updateBalance(account = account, orders = orders)
        positionService <- ZIO.service[PositionService]
        closeOpenPositionsCount <- positionService.closeOpenPositions(
          openPositions = openPositions,
          orders = orders
        )
        _ <- ZIO.attempt(logger.info(s"Positions closed : $closeOpenPositionsCount"))
        openPositionsCount <- positionService.createOpenPositionsFromOrders(
          orders = orders
        )
        _ <- ZIO.attempt(logger.info(s"Positions opened : $openPositionsCount"))
      yield ()
    })

  private def updateBalance(
      account: Account,
      orders: List[Order]
  ): ZIO[Connection & AppEnv, Throwable, Option[Unit]] =
    val newBalance = totalBalance(currentBalance = account.balance, orders = orders)
    ZIO.when(orders.nonEmpty)(ZIO.scoped {
      for
        _ <- ZIO.attempt(logger.info(s"Balance updated to $newBalance !"))
        accountService <- ZIO.service[AccountService]
        _ <- accountService.updateBalance(
          id = account.id,
          newBalance = newBalance
        )
      yield ()
    })

  private def totalBalance(currentBalance: Double, orders: List[Order]): Double =
    val gain = orders.filter(_.`type` == OrderType.Sell).map(_.totalPrice).sum
    val loss = orders.filter(_.`type` == OrderType.Buy).map(_.totalPrice).sum
    (currentBalance + gain) - loss

  private def getBalancePerFinInst(
      account: Account,
      finInstruments: List[FinInstrument]
  ): Double =
    if finInstruments.isEmpty then 0.0d
    else account.balance / finInstruments.size

  private def getTradingExchangeMap(
      openPositions: List[Position]
  ): ZIO[Connection & AppEnv, Throwable, Map[String, TradingExchange]] =
    // TODO filter by symbols
    val symbols = openPositions.map(_.symbol).toSet
    ZIO
      .when(openPositions.nonEmpty)(ZIO.scoped {
        for
          tradingExchangeService <- ZIO.service[TradingExchangeService]
          _ <- ZIO.attempt(logger.info(s"Getting exchanges for symbols ${symbols}... "))
          exchanges <- tradingExchangeService.findAll()
          _ <- ZIO.attempt(logger.info(s"exchanges: $exchanges"))
        yield exchanges.groupBy(_.id).view.mapValues(_.head).toMap
      })
      .map {
        case Some(value) => value
        case None        => Map.empty
      }

  implicit val errorRecovery: ErrorStrategiesRef =
    DatabaseConfig.alternateDbRecovery

  private def runTradingLoop(): ZIO[AppEnv, Throwable, Unit] =
    Database.transactionOrWiden(executeTradingTransaction)
