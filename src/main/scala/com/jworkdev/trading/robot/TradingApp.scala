package com.jworkdev.trading.robot

import com.jworkdev.trading.robot.domain.{
  Account,
  FinInstrumentConfig,
  Position
}
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

  override def run: ZIO[ZIOAppArgs & Scope, Any, Any] =
    for
      _ <- Console.printLine("Starting the app")
      trio <- runTradingLoop().provideLayer(appEnv)
      _ <- Console.printLine(trio.mkString(", "))
    yield ExitCode(0)

  private def updateBalance(
                           account: Account,
                           orders: List[Order]
                         ): ZIO[Connection & AppEnv, Throwable, Option[Unit]] = {
    val newBalance = orders.map(_.totalPrice).sum
    ZIO.when(orders.nonEmpty)(ZIO.scoped {
      for {
        accountService <- ZIO.service[AccountService]
        _ <- accountService.updateBalance(
          id = account.id,
          newBalance = newBalance
        )
      } yield ()
    })
  }

  private def applyOrders(
      account: Account,
      openPositions: List[Position],
      orders: List[Order]
  ): ZIO[Connection & AppEnv, Throwable, Unit] =
    for
      newBalance <- ZIO.succeed(
        orders.map(_.totalPrice).sum
      )
      _ <- updateBalance(account= account, orders = orders)
      positionService <- ZIO.service[PositionService]
      _ <- positionService.closeOpenPositions(
        openPositions = openPositions,
        orders = orders
      )
      _ <- positionService.createOpenPositionsFromOrders(
        orders = orders
      )
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
      orders <- tradingExecutorService.execute(
        balancePerFinInst = balancePerFinInst,
        finInstrumentConfigs = finInstrumentConfigs,
        openPositions = openPositions
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
