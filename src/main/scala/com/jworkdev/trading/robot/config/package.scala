package com.jworkdev.trading.robot

import com.jworkdev.trading.robot.market.data.SnapshotInterval
import zio.{Config, ConfigProvider, IO}
import zio.config.magnolia.*
import zio.config.typesafe.*

package object config {
  case class DataBaseConfig(
                       url: String,
                       user: String,
                       password: String,
                       driver: String
                     )
  case class MACDStrategyConfiguration(snapshotInterval: SnapshotInterval)
  case class StrategyConfigurations(macd: Option[MACDStrategyConfiguration])
  case class ApplicationConfiguration(dataBaseConfig: DataBaseConfig,
                                      strategyConfigurations: StrategyConfigurations)

  val appConfig: IO[Config.Error, ApplicationConfiguration] = ConfigProvider
    .fromHoconFile(new java.io.File("config/application.conf"))
    .load(deriveConfig[ApplicationConfiguration])
}
