package com.jworkdev.trading.robot

import com.zaxxer.hikari.HikariDataSource

import javax.sql.DataSource

package object infra:

  import com.zaxxer.hikari.HikariConfig
  import zio.*
  object DatabaseConfig:
    private val hikariConfig: HikariConfig = {
      val hikariConfig = new HikariConfig()
      hikariConfig.setJdbcUrl("jdbc:mysql://localhost:3306/robot_trading")
      hikariConfig.setUsername("trading")
      hikariConfig.setPassword("trading")
      hikariConfig.setDriverClassName("com.mysql.cj.jdbc.Driver")
      hikariConfig.setMaximumPoolSize(10)
      hikariConfig
    }

    val live: ZLayer[Any, Throwable, DataSource] =
      ZLayer.fromZIO(ZIO.serviceWithZIO[Any] { _ =>
        ZIO.attemptBlocking {
          val ds: DataSource = new HikariDataSource(hikariConfig)
          ds
        }
      })
