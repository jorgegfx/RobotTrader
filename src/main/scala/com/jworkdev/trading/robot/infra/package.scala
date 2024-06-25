package com.jworkdev.trading.robot

import com.zaxxer.hikari.HikariDataSource
import doobie.util.transactor
import io.github.gaelrenoux.tranzactio.doobie.Database
import io.github.gaelrenoux.tranzactio.{DatabaseOps, ErrorStrategies}
import zio.config.magnolia.*
import zio.config.typesafe.*
import zio.{Config, ConfigProvider, IO}

import javax.sql.DataSource

package object infra:

  private case class DbConfig(
      url: String,
      user: String,
      password: String,
      driver: String
  )
  private val dbConfig: IO[Config.Error, DbConfig] = ConfigProvider
    .fromHoconFile(new java.io.File("config/application.conf"))
    .load(deriveConfig[DbConfig])

  import com.zaxxer.hikari.HikariConfig
  import zio.*
  object DatabaseConfig:

    private def hikariConfig(dbConfig: DbConfig): HikariConfig =
      val hikariConfig = new HikariConfig()
      hikariConfig.setJdbcUrl(dbConfig.url)
      hikariConfig.setUsername(dbConfig.user)
      hikariConfig.setPassword(dbConfig.password)
      hikariConfig.setDriverClassName(dbConfig.driver)
      hikariConfig.setMaximumPoolSize(10)
      hikariConfig

    val layer: ZLayer[Any, Throwable, DataSource] = ZLayer.scoped {
      for
        cfg <- dbConfig.map(dbCfg => hikariConfig(dbConfig = dbCfg))
        ds <- ZIO.attemptBlocking {
          val ds: DataSource = new HikariDataSource(cfg)
          ds
        }
      yield ds
    }

    private val dbRecovery = ZLayer.succeed(
      ErrorStrategies
        .timeout(10.seconds)
        .retryForeverExponential(10.seconds, maxDelay = 10.seconds)
    )
    private val datasource = DatabaseConfig.dbRecovery >>> DatabaseConfig.layer
    val database: ZLayer[Any, Throwable, DatabaseOps.ServiceOps[
      transactor.Transactor[Task]
    ]] =
      (datasource ++ dbRecovery) >>> Database.fromDatasourceAndErrorStrategies
    val alternateDbRecovery: ErrorStrategies =
      ErrorStrategies.timeout(10.seconds).retryCountFixed(3, 3.seconds)
