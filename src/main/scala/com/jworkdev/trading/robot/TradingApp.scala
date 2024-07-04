package com.jworkdev.trading.robot

import com.jworkdev.trading.robot.config.appConfig
import com.jworkdev.trading.robot.domain.{Account, FinInstrumentConfig, Position}
import com.jworkdev.trading.robot.infra.*
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
  private val finInstrumentConfigService = FinInstrumentConfigService.layer
  private val tradingExecutorService = TradingExecutorService()
  type AppEnv = Database & AccountService & PositionService &
    FinInstrumentConfigService
  private val appEnv =
    DatabaseConfig.database ++ accountService ++ positionService ++ finInstrumentConfigService
  // Define the interval in minutes
  private val intervalMinutes: Int = 1
  private val schedule: Schedule[Any, Any, Long] = Schedule.fixed(intervalMinutes.minutes)

  override def run: ZIO[ZIOAppArgs & Scope, Any, Any] =
    for
      _ <- Console.printLine("Starting the app ...")
      _ <- periodicTask.repeat(schedule)
      _ <- Console.printLine("Finished!")
    yield ExitCode(0)

  // Task to be executed periodically
  private val periodicTask: ZIO[Any, Throwable, Unit] = for {
      currentTime <- Clock.currentDateTime
      _ <- runTradingLoop().provideLayer(appEnv)
      _ <- Console.printLine(s"Task executed at: $currentTime")
  } yield ()


  private def totalBalance(currentBalance: Double, orders: List[Order]): Double = {
    val gain = orders.filter(_.`type`==OrderType.Sell).map(_.totalPrice).sum
    val loss = orders.filter(_.`type`==OrderType.Buy).map(_.totalPrice).sum
    (currentBalance + gain) - loss
  }

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
  ): ZIO[Connection & AppEnv, Throwable, Unit] =
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

  private def getBalancePerFinInst(
      account: Account,
      finInstrumentConfigs: List[FinInstrumentConfig]
  ): Double =
    if finInstrumentConfigs.isEmpty then 0.0d
    else account.balance / finInstrumentConfigs.size

  private def runTradingLoop(): ZIO[AppEnv, Throwable, List[Order]] =
    val executeTradingTransactions
        : ZIO[Connection & AppEnv, Throwable, List[Order]] = for
      accountService <- ZIO.service[AccountService]
      account <- accountService.findByName("trading")
      finInstrumentConfigService <- ZIO.service[FinInstrumentConfigService]
      finInstrumentConfigs <- finInstrumentConfigService.findAll()
      balancePerFinInst <- ZIO.succeed(
        getBalancePerFinInst(
          account = account,
          finInstrumentConfigs = finInstrumentConfigs
        )
      )
      positionService <- ZIO.service[PositionService]
      openPositions <- positionService.findAllOpen()
      _ <- Console.printLine("Executing orders ...")
      strategyCfgs <- appConfig.map(appCfg => appCfg.strategyConfigurations)
      orders <- tradingExecutorService.execute(
        balancePerFinInst = balancePerFinInst,
        finInstrumentConfigs = finInstrumentConfigs,
        openPositions = openPositions,
        strategyConfigurations = strategyCfgs
      )
      _ <- applyOrders(
        account = account,
        openPositions = openPositions,
        orders = orders
      )
    yield orders

    ZIO.serviceWithZIO[Any] { _ =>
      // if this implicit is not provided, tranzactio will use Conf.dbRecovery instead
      implicit val errorRecovery: ErrorStrategiesRef =
        DatabaseConfig.alternateDbRecovery
      Database.transactionOrWiden(executeTradingTransactions)
    }
