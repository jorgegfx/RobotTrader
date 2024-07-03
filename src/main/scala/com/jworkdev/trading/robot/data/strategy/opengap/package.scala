package com.jworkdev.trading.robot.data.strategy
import com.jworkdev.trading.robot.data.signals

package object opengap {
  case class OpenGapMarketDataStrategyRequest(signalCount: Int) extends MarketDataStrategyRequest
  case class OpenGapSignalInput(previousClose: Double,
                                currentOpening: Double,
                                currentPrice: Double,
                                volumeAvg: Double,
                                currentVolume: Double)
  case class OpenGapMarketDataStrategyResponse(
                                                signalInputs: List[OpenGapSignalInput]
                                              ) extends MarketDataStrategyResponse{
    override def buildSignalFinderRequest(): signals.SignalFinderRequest = ???
  }


}
