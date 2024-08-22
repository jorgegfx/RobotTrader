package com.jworkdev.trading.robot.infra

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

object H2Database:
  private def hikariConfig(): HikariConfig =
    val hikariConfig = new HikariConfig()
    hikariConfig.setJdbcUrl("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1")
    hikariConfig.setUsername("sa")
    hikariConfig.setPassword("")
    hikariConfig.setDriverClassName("org.h2.Driver")
    hikariConfig.setMaximumPoolSize(10)
    hikariConfig

  val dbLayer: ZLayer[Any, Throwable, DataSource] = ZLayer.scoped {
    for ds <- ZIO.attemptBlocking(new HikariDataSource(hikariConfig())) yield ds
  }
  
  implicit val dbContext: DbContext =
    DbContext(logHandler = LogHandler.jdkLogHandler[Task])

  val dbRecovery = ZLayer.succeed(
    ErrorStrategies
      .timeout(10.seconds)
      .retryForeverExponential(10.seconds, maxDelay = 10.seconds)
  )
  val datasource = dbRecovery >>> dbLayer

  val database: ZLayer[Any, Throwable, DatabaseOps.ServiceOps[
    transactor.Transactor[Task]
  ]] =
    (datasource ++ dbRecovery) >>> Database.fromDatasourceAndErrorStrategies
  val alternateDbRecovery: ErrorStrategies =
    ErrorStrategies.timeout(10.seconds).retryCountFixed(3, 3.seconds)

