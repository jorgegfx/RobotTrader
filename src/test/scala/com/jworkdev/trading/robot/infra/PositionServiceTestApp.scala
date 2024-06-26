package com.jworkdev.trading.robot.infra

import com.jworkdev.trading.robot.domain.Position
import doobie.util.log.LogHandler
import io.github.gaelrenoux.tranzactio.doobie.*
import io.github.gaelrenoux.tranzactio.{DbException, ErrorStrategiesRef}
import zio.*
import zio.Console.*
import zio.interop.catz.*

import java.time.Instant

object PositionServiceTestApp extends zio.ZIOAppDefault:
  implicit val dbContext: DbContext = DbContext(logHandler = LogHandler.jdkLogHandler[Task])
  private val positionService = PositionService.layer
  type AppEnv = Database & PositionService
  private val appEnv = DatabaseConfig.database ++ positionService

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
      implicit val errorRecovery: ErrorStrategiesRef = DatabaseConfig.alternateDbRecovery
      Database.transactionOrWiden(queries)
    }
  }

