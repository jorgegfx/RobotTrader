package com.jworkdev.trading.robot

import com.jworkdev.trading.robot.config.{ApplicationConfiguration, DataBaseConfig}
import com.zaxxer.hikari.HikariDataSource
import doobie.util.transactor
import io.github.gaelrenoux.tranzactio.doobie.{Database, DbContext}
import io.github.gaelrenoux.tranzactio.{DatabaseOps, ErrorStrategies}
import zio.config.magnolia.*
import zio.config.typesafe.*
import zio.{Config, ConfigProvider, IO}
import com.jworkdev.trading.robot.config.appConfig
import com.jworkdev.trading.robot.service.{AccountService, FinInstrumentService, PositionService, TradingExchangeService, TradingStrategyService, *}
import doobie.LogHandler

import javax.sql.DataSource

package object infra:

  import com.zaxxer.hikari.HikariConfig
  import zio.*

  type AppEnv = Database & AccountService & PositionService & FinInstrumentService & TradingStrategyService &
    TradingExchangeService & OrderService & PnLPerformanceService

  object DatabaseConfig:

    private def hikariConfig(dbConfig: DataBaseConfig): HikariConfig =
      val hikariConfig = new HikariConfig()
      hikariConfig.setJdbcUrl(dbConfig.url)
      hikariConfig.setUsername(dbConfig.user)
      hikariConfig.setPassword(dbConfig.password)
      hikariConfig.setDriverClassName(dbConfig.driver)
      hikariConfig.setMaximumPoolSize(10)
      hikariConfig

    val layer: ZLayer[Any, Throwable, DataSource] = ZLayer.scoped {
      for
        cfg <- appConfig.map(appCfg => hikariConfig(dbConfig = appCfg.dataBaseConfig))
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

    private val accountService = AccountService.layer
    private val positionService = PositionService.layer
    private val tradingStrategyService = TradingStrategyService.layer
    private val finInstrumentService = FinInstrumentService.layer
    private val tradingExchangeService = TradingExchangeService.layer
    private val orderService = OrderService.layer
    private val pnLPerformanceService = PnLPerformanceService.layer
    val appEnv =
      DatabaseConfig.database ++ accountService ++
        positionService ++ finInstrumentService ++
        tradingStrategyService ++ tradingExchangeService ++
        orderService ++ pnLPerformanceService

