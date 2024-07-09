package com.jworkdev.trading.robot

import com.jworkdev.trading.robot.config.appConfig
import com.jworkdev.trading.robot.domain.{Account, FinInstrument, Position}
import com.jworkdev.trading.robot.infra.{TradingStrategyService, *}
import com.jworkdev.trading.robot.service.{AccountService, FinInstrumentService, PositionService, TradingExecutorService, TradingStrategyService}
import doobie.util.log.LogHandler
import io.github.gaelrenoux.tranzactio.ErrorStrategiesRef
import io.github.gaelrenoux.tranzactio.doobie.*
import zio.*
import zio.Console.*
import zio.interop.catz.*

object TradingApp extends zio.ZIOAppDefault:
  implicit val dbContext: DbContext =
    DbContext(logHandler = LogHandler.jdkLogHandler[Task])
  private val accountService = AccountService.layer
  private val positionService = PositionService.layer
  private val tradingStrategyService = TradingStrategyService.layer
  private val finInstrumentService = FinInstrumentService.layer
  private val tradingExecutorService = TradingExecutorService()
  type AppEnv = Database & AccountService & PositionService & FinInstrumentService & TradingStrategyService
  private val appEnv =
    DatabaseConfig.database ++ accountService ++ positionService ++ finInstrumentService ++ tradingStrategyService
  // Define the interval in minutes
  private val intervalMinutes: Int = 1
  private val schedule: Schedule[Any, Any, Long] = Schedule.fixed(intervalMinutes.minutes)

  override def run: ZIO[ZIOAppArgs & Scope, Any, Any] =
    periodicTask.repeat(schedule).provideLayer(appEnv).exitCode

  // Task to be executed periodically
  private val periodicTask: ZIO[AppEnv, Throwable, Unit] = for
    currentTime <- Clock.currentDateTime
    _ <- runTradingLoop()
    _ <- Console.printLine(s"Task executed at: $currentTime")
  yield ()

  private def totalBalance(currentBalance: Double, orders: List[Order]): Double =
    val gain = orders.filter(_.`type` == OrderType.Sell).map(_.totalPrice).sum
    val loss = orders.filter(_.`type` == OrderType.Buy).map(_.totalPrice).sum
    (currentBalance + gain) - loss

  private def updateBalance(
      account: Account,
      orders: List[Order]
  ): ZIO[Connection & AppEnv, Throwable, Option[Unit]] =
    val newBalance = totalBalance(currentBalance = account.balance, orders = orders)
    ZIO.when(orders.nonEmpty)(ZIO.scoped {
      for
        _ <- Console.printLine(s"Balance updated to $newBalance !")
        accountService <- ZIO.service[AccountService]
        _ <- accountService.updateBalance(
          id = account.id,
          newBalance = newBalance
        )
      yield ()
    })

  private def applyOrders(
      account: Account,
      openPositions: List[Position],
      orders: List[Order]
  ): ZIO[Connection & AppEnv, Throwable, Option[Unit]] =
    ZIO.when(orders.nonEmpty)(ZIO.scoped {
      for
        _ <- Console.printLine(s"Orders created :$orders")
        _ <- updateBalance(account = account, orders = orders)
        positionService <- ZIO.service[PositionService]
        _ <- positionService.closeOpenPositions(
          openPositions = openPositions,
          orders = orders
        )
        _ <- Console.printLine(s"Positions closed!")
        _ <- positionService.createOpenPositionsFromOrders(
          orders = orders
        )
        _ <- Console.printLine(s"Positions opened!")
      yield ()
    })

  private def getBalancePerFinInst(
      account: Account,
      finInstruments: List[FinInstrument]
  ): Double =
    if finInstruments.isEmpty then 0.0d
    else account.balance / finInstruments.size

  private val executeTradingTransaction: ZIO[Connection & AppEnv, Throwable, Unit] = for
    accountService <- ZIO.service[AccountService]
    account <- accountService.findByName("trading")
    finInstrumentConfigService <- ZIO.service[FinInstrumentService]
    finInstruments <- finInstrumentConfigService.findAll()
    balancePerFinInst <- ZIO.succeed(
      getBalancePerFinInst(
        account = account,
        finInstruments = finInstruments
      )
    )
    positionService <- ZIO.service[PositionService]
    strategyService <- ZIO.service[TradingStrategyService]
    tradingStrategies <- strategyService.findAll()
    openPositions <- positionService.findAllOpen()
    _ <- Console.printLine("Executing orders ...")
    strategyCfgs <- appConfig.map(appCfg => appCfg.strategyConfigurations)
    orders <- tradingExecutorService.execute(
      balancePerFinInst = balancePerFinInst,
      finInstruments = finInstruments,
      tradingStrategies = tradingStrategies,
      openPositions = openPositions,
      strategyConfigurations = strategyCfgs
    )
    _ <- applyOrders(
      account = account,
      openPositions = openPositions,
      orders = orders
    )
  yield ()

  implicit val errorRecovery: ErrorStrategiesRef =
    DatabaseConfig.alternateDbRecovery

  private def runTradingLoop(): ZIO[AppEnv, Throwable, Unit] =
    Database.transactionOrWiden(executeTradingTransaction)
