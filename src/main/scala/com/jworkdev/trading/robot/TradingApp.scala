package com.jworkdev.trading.robot

import com.jworkdev.trading.robot.config.appConfig
import com.jworkdev.trading.robot.domain.{Account, FinInstrument, Position, TradingExchange}
import com.jworkdev.trading.robot.infra.{TradingStrategyService, *}
import com.jworkdev.trading.robot.service.{
  AccountService,
  FinInstrumentService,
  PositionService,
  TradingExchangeService,
  TradingExecutorRequest,
  TradingExecutorService,
  TradingStrategyService
}
import doobie.util.log.LogHandler
import io.github.gaelrenoux.tranzactio.ErrorStrategiesRef
import io.github.gaelrenoux.tranzactio.doobie.*
import zio.*
import zio.Console.*
import zio.interop.catz.*

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
  // Task to be executed periodically
  private val periodicTask: ZIO[AppEnv, Throwable, Unit] = for
    _ <- ZIO.logInfo(s"Starting ...")
    currentTime <- Clock.currentDateTime
    _ <- runTradingLoop()
    _ <- ZIO.logInfo(s"Task executed at: $currentTime")
  yield ()
  private val executeTradingTransaction: ZIO[Connection & AppEnv, Throwable, Unit] = for
    accountService <- ZIO.service[AccountService]
    account <- accountService.findByName("trading")
    _ <- ZIO.logInfo(s"Trading with account name :'${account.name}''")
    finInstrumentService <- ZIO.service[FinInstrumentService]
    finInstruments <- finInstrumentService.findTopToTrade()
    _ <- ZIO.logInfo(s"Searching signals on ${finInstruments.map(_.symbol)}")
    balancePerFinInst <- ZIO.attempt(
      getBalancePerFinInst(
        account = account,
        finInstruments = finInstruments
      )
    )
    _ <- ZIO.logInfo(s"balancePerFinInst : $balancePerFinInst")
    positionService <- ZIO.service[PositionService]
    strategyService <- ZIO.service[TradingStrategyService]
    tradingStrategies <- strategyService.findAll()
    _ <- ZIO.logInfo(s"tradingStrategies : $tradingStrategies")
    openPositions <- positionService.findAllOpen()
    _ <- ZIO.logInfo(s"openPositions : $openPositions")
    exchangeMap <- getTradingExchangeMap(openPositions = openPositions)
    _ <- ZIO.logInfo(s"exchangeMap : $exchangeMap")
    strategyCfgs <- appConfig.map(_.strategyConfigurations)
    stopLossPercentage <- appConfig.map(_.stopLossPercentage)
    tradingMode <- appConfig.map(_.tradingMode)
    _ <- ZIO.logInfo("Executing orders ...")
    orders <- tradingExecutorService.execute(
      TradingExecutorRequest(
        balancePerFinInst = balancePerFinInst,
        finInstruments = finInstruments,
        tradingStrategies = tradingStrategies,
        openPositions = openPositions,
        exchangeMap = exchangeMap,
        strategyConfigurations = strategyCfgs,
        stopLossPercentage = stopLossPercentage,
        tradingMode = tradingMode
      )
    )
    _ <- ZIO.logInfo(s"Orders created :$orders ...")
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
        _ <- ZIO.logInfo(s"Applying orders :$orders ...")
        _ <- updateBalance(account = account, orders = orders)
        positionService <- ZIO.service[PositionService]
        _ <- positionService.closeOpenPositions(
          openPositions = openPositions,
          orders = orders
        )
        _ <- ZIO.logInfo(s"Positions closed!")
        _ <- positionService.createOpenPositionsFromOrders(
          orders = orders
        )
        _ <- ZIO.logInfo(s"Positions opened!")
      yield ()
    })

  private def updateBalance(
      account: Account,
      orders: List[Order]
  ): ZIO[Connection & AppEnv, Throwable, Option[Unit]] =
    val newBalance = totalBalance(currentBalance = account.balance, orders = orders)
    ZIO.when(orders.nonEmpty)(ZIO.scoped {
      for
        _ <- ZIO.logInfo(s"Balance updated to $newBalance !")
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
    //TODO filter by symbols
    val symbols = openPositions.map(_.symbol).toSet
    ZIO
      .when(openPositions.nonEmpty)(ZIO.scoped {
        for
          tradingExchangeService <- ZIO.service[TradingExchangeService]
          _ <- ZIO.logInfo(s"Getting exchanges for symbols ${symbols}... ")
          exchanges <- tradingExchangeService.findAll()
          _ <- ZIO.logInfo(s"exchanges: $exchanges")
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
