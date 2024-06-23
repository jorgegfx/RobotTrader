package com.jworkdev.trading.robot.infra

import com.jworkdev.trading.robot.domain.Position
import doobie.util.log.LogHandler
import io.github.gaelrenoux.tranzactio.doobie.*
import io.github.gaelrenoux.tranzactio.{DbException, ErrorStrategies, ErrorStrategiesRef}
import zio.*
import zio.interop.catz.*
import zio.Console.*

import java.time.Instant

object PositionServiceTestApp extends zio.ZIOAppDefault:
  private val dbRecovery = ZLayer.succeed(ErrorStrategies.timeout(10.seconds).retryForeverExponential(10.seconds, maxDelay = 10.seconds))
  private val datasource = dbRecovery >>> DatabaseConfig.live
  implicit val dbContext: DbContext = DbContext(logHandler = LogHandler.jdkLogHandler[Task])
  private val database = (datasource ++ dbRecovery) >>> Database.fromDatasourceAndErrorStrategies
  private val positionService = PositionServiceLayer.layer
  private val alternateDbRecovery = ErrorStrategies.timeout(10.seconds).retryCountFixed(3, 3.seconds)
  type AppEnv = Database with PositionService
  private val appEnv = database ++ positionService


  override def run: ZIO[ZIOAppArgs with Scope, Any, Any] =
    for {
      _ <- Console.printLine("Starting the app")
      trio <- myApp().provideLayer(appEnv)
      _ <- Console.printLine(trio.mkString(", "))
    } yield ExitCode(0)

  def myApp(): ZIO[AppEnv, DbException, List[Position]] = {
    val queries: ZIO[Connection with AppEnv, DbException, List[Position]] = for {
      positionService <- ZIO.service[PositionService]
      _ <- positionService.create(
        Position(
          id = 0,
          symbol = "AAPL",
          numberOfShares = 2,
          openPricePerShare = 100,
          closePricePerShare = None,
          openDate = Instant.now,
          closeDate = None,
          pnl = None
        )
      )
      positions <- positionService.findAll()
    } yield positions

    ZIO.serviceWithZIO[Any] { _ =>
      // if this implicit is not provided, tranzactio will use Conf.dbRecovery instead
      implicit val errorRecovery: ErrorStrategiesRef = alternateDbRecovery
      Database.transactionOrWiden(queries)
    }
  }

