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
  enum TradingMode:
    case IntraDay, Swing

  case class MACDStrategyConfiguration(snapshotInterval: SnapshotInterval)
  case class OpenGapStrategyConfiguration(signalCount: Int)
  case class StrategyConfigurations(macd: Option[MACDStrategyConfiguration] = None,
                                    openGap: Option[OpenGapStrategyConfiguration] = None)
  case class ApplicationConfiguration(dataBaseConfig: DataBaseConfig,
                                      strategyConfigurations: StrategyConfigurations,
                                      stopLossPercentage: Int,
                                      tradingMode: TradingMode)

  val appConfig: IO[Config.Error, ApplicationConfiguration] = ConfigProvider
    .fromHoconFile(new java.io.File("config/application.conf"))
    .load(deriveConfig[ApplicationConfiguration])
}
