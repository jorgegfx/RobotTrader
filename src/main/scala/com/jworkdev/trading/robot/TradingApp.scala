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
import com.jworkdev.trading.robot.infra.DatabaseConfig.appEnv
import java.time.ZonedDateTime

object TradingApp extends zio.ZIOAppDefault:
  implicit val dbContext: DbContext =
    DbContext(logHandler = LogHandler.jdkLogHandler[Task])
  private val tradingExecutorService = TradingExecutorService()
  private val orderBalancer = OrderBalancer()

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
      finInstruments: List[FinInstrument],
      openPositions: List[Position]
  ): ZIO[Connection & AppEnv, Throwable, Map[FinInstrument, List[Position]]] =
    for
      allFinInstruments <- fetchAllFinInstrument(finInstruments = finInstruments, openPositions = openPositions)
    yield allFinInstruments
      .map(finInstrument => (finInstrument, openPositions.filter(position => position.symbol == finInstrument.symbol)))
      .toMap

  private val executeTradingTransaction: ZIO[Connection & AppEnv, Throwable, Unit] = for
    strategyCfgs <- appConfig.map(_.strategyConfigurations)
    stopLossPercentage <- appConfig.map(_.stopLossPercentage)
    takeProfitPercentage <- appConfig.map(_.takeProfitPercentage)
    screenCount <- appConfig.map(_.screenCount)
    tradingMode <- appConfig.map(_.tradingMode)
    accountName <- appConfig.map(_.accountName)
    //find the account we will use for trading
    account <- ZIO.serviceWithZIO[AccountService](_.findByName(name=accountName))
    _ <- ZIO.attempt(logger.info(s"Trading with account name :'${account.name}''"))
    //find the open positions we are trying to close
    openPositions <- ZIO.serviceWithZIO[PositionService](_.findAllOpen())
    _ <- ZIO.attempt(logger.info(s"openPositions : $openPositions"))
    //We make the symbol screening
    finInstruments <- ZIO.serviceWithZIO[FinInstrumentService](_.findTopToTrade(limit = screenCount))
    _ <- ZIO.attempt(logger.info(s"Searching signals on ${finInstruments.map(_.symbol)}"))
    //we calculate the maximum capital to trade on each position to open
    maxTradingCapitalPerTrade <- ZIO.attempt(
      calculateMaxTradingCapitalPerTrade(
        account = account,
        finInstruments = finInstruments
      )
    )
    _ <- ZIO.attempt(logger.info(s"maxTradingCapitalPerTrade : $maxTradingCapitalPerTrade"))
    //we find all
    tradingStrategies <- ZIO.serviceWithZIO[TradingStrategyService](_.findAll())
    _ <- ZIO.attempt(logger.info(s"tradingStrategies : $tradingStrategies"))
    //
    exchangeMap <- getTradingExchangeMap
    _ <- ZIO.attempt(logger.info(s"exchangeMap : $exchangeMap"))
    _ <- ZIO.attempt(logger.info("Executing orders ..."))
    finInstrumentMap <- buildFinInstrumentMap(finInstruments = finInstruments, openPositions = openPositions)
    orders <- tradingExecutorService.execute(
      TradingExecutorRequest(
        maxTradingCapitalPerTrade = maxTradingCapitalPerTrade,
        finInstrumentMap = finInstrumentMap,
        tradingStrategies = tradingStrategies,
        exchangeMap = exchangeMap,
        strategyConfigurations = strategyCfgs,
        stopLossPercentage = stopLossPercentage,
        takeProfitPercentage = takeProfitPercentage,
        tradingMode = tradingMode,
        tradingDateTime = ZonedDateTime.now()
      )
    )
    _ <- executeOrders(
      account = account,
      openPositions = openPositions,
      orders = orders
    )
  yield ()

  override def run: ZIO[ZIOAppArgs & Scope, Any, Any] =
    periodicTask.repeat(schedule).provideLayer(appEnv).exitCode

  private def executeOrders(
      account: Account,
      openPositions: List[Position],
      orders: List[Order]
  ): ZIO[Connection & AppEnv, Throwable, Option[Unit]] =
    ZIO.when(orders.nonEmpty)(ZIO.scoped {
      for
        _ <- ZIO.attempt(logger.info(s"Orders created :$orders ..."))
        _ <- ZIO.serviceWithZIO[OrderService](_.create(orders = orders))
        _ <- ZIO.attempt(logger.info(s"Applying orders :$orders ..."))
        balancedOrders <- ZIO.attempt(orderBalancer.balance(amount = account.balance,orders = orders))
        _ <- ZIO.attempt(logger.info(s"Balanced orders :$balancedOrders ..."))
        _ <- updateBalance(account = account, orders = balancedOrders)
        positionService <- ZIO.service[PositionService]
        closeOpenPositionsCount <- positionService.closeOpenPositions(
          openPositions = openPositions,
          orders = balancedOrders
        )
        _ <- ZIO.attempt(logger.info(s"Positions closed : $closeOpenPositionsCount"))
        openPositionsCount <- positionService.createOpenPositionsFromOrders(
          orders = balancedOrders
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

  private def calculateMaxTradingCapitalPerTrade(
      account: Account,
      finInstruments: List[FinInstrument]
  ): Double =
    if finInstruments.isEmpty then 0.0d
    else account.balance / finInstruments.size

  private def getTradingExchangeMap: ZIO[Connection & AppEnv, Throwable, Map[String, TradingExchange]] =
    ZIO.scoped {
        for
          tradingExchangeService <- ZIO.service[TradingExchangeService]
          exchanges <- tradingExchangeService.findAll()
          _ <- ZIO.attempt(logger.info(s"exchanges: $exchanges"))
        yield exchanges.groupBy(_.id).view.mapValues(_.head).toMap
    }

  implicit val errorRecovery: ErrorStrategiesRef =
    DatabaseConfig.alternateDbRecovery

  private def runTradingLoop(): ZIO[AppEnv, Throwable, Unit] =
    Database.transactionOrWiden(executeTradingTransaction)
