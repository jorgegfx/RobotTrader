package com.jworkdev.trading.robot.infra

import com.jworkdev.trading.robot.domain.{Position, TradingStrategyType}
import com.jworkdev.trading.robot.service.PositionService
import com.jworkdev.trading.robot.service.TradingExecutorServiceSpec.{suite, test}
import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import doobie.util.log.LogHandler
import doobie.util.transactor
import doobie.util.transactor.Transactor
import io.github.gaelrenoux.tranzactio.doobie.*
import io.github.gaelrenoux.tranzactio.{DatabaseOps, ErrorStrategies, ErrorStrategiesRef}
import zio.*
import zio.interop.catz.*
import zio.test.{ZIOSpecDefault, test, *}

import java.time.ZonedDateTime
import javax.sql.DataSource

object PositionServiceSpec extends ZIOSpecDefault {
  private def hikariConfig(): HikariConfig =
    val hikariConfig = new HikariConfig()
    hikariConfig.setJdbcUrl("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1")
    hikariConfig.setUsername("sa")
    hikariConfig.setPassword("")
    hikariConfig.setDriverClassName("org.h2.Driver")
    hikariConfig.setMaximumPoolSize(10)
    hikariConfig
  private val testPosition = Position(id = 0,
    symbol = "NVDA",
    numberOfShares = 2,
    openPricePerShare = 10,
    closePricePerShare = None,
    openDate = ZonedDateTime.now(),
    closeDate = None,
    pnl = None,
    tradingStrategyType = TradingStrategyType.MACD)
  val dbLayer: ZLayer[Any, Throwable, DataSource] = ZLayer.scoped {
    for
      ds <- ZIO.attemptBlocking { new HikariDataSource(hikariConfig()) }
    yield ds
  }
  implicit val dbContext: DbContext =
    DbContext(logHandler = LogHandler.jdkLogHandler[Task])

  val positionServiceLayer: ULayer[PositionService] =
    ZLayer.succeed(new PositionServiceImpl)

  private val dbRecovery = ZLayer.succeed(
    ErrorStrategies
      .timeout(10.seconds)
      .retryForeverExponential(10.seconds, maxDelay = 10.seconds)
  )
  private val datasource = dbRecovery >>> DatabaseConfig.layer
  
  val database: ZLayer[Any, Throwable, DatabaseOps.ServiceOps[
    transactor.Transactor[Task]
  ]] =
    (datasource ++ dbRecovery) >>> Database.fromDatasourceAndErrorStrategies
  val alternateDbRecovery: ErrorStrategies =
    ErrorStrategies.timeout(10.seconds).retryCountFixed(3, 3.seconds)
  
  private val appEnv =
    DatabaseConfig.database ++ positionServiceLayer

  type AppEnv = Database & PositionService

  implicit val errorRecovery: ErrorStrategiesRef =
    DatabaseConfig.alternateDbRecovery

  override def spec: Spec[Any, Throwable] = suite("testExecute")(
    test("findAll") {
      val createAndGetPosition = for {
        _ <- ZIO.serviceWithZIO[PositionService](_.create(position = testPosition))
        positions <- ZIO.serviceWithZIO[PositionService](_.findAll())
      } yield positions.last
      for 
        position <- Database.transactionOrWiden(createAndGetPosition).provideLayer(appEnv)
      yield assertTrue(position == testPosition)  
    }
  )
}
