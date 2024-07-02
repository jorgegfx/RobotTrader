package com.jworkdev.trading.robot.data.strategy

package object opengap {
  case class OpenGapMarketDataStrategyRequest(signalCount: Int) extends MarketDataStrategyRequest
  case class OpenGapSignalInput(previousClose: Double,
                                currentOpening: Double,
                                currentPrice: Double,
                                volumeAvg: Double,
                                currentVolume: Double)
  case class OpenGapMarketDataStrategyResponse(
                                                signalInputs: List[OpenGapSignalInput]
                                              ) extends MarketDataStrategyResponse


}
