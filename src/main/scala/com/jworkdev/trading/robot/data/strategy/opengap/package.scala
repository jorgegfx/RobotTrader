package com.jworkdev.trading.robot.data.strategy
import com.jworkdev.trading.robot.data.signals
import com.jworkdev.trading.robot.data.signals.OpenGapRequest
import com.jworkdev.trading.robot.market.data.StockPrice

package object opengap {
  case class OpenGapMarketDataStrategyRequest(symbol: String, signalCount: Int) extends MarketDataStrategyRequest
  case class OpenGapSignalInput(closingPrice: Double,
                                openingPrice: Double,
                                volumeAvg: Double,
                                currentPrice: StockPrice)
  case class OpenGapMarketDataStrategyResponse(
                                                signalInputs: List[OpenGapSignalInput]
                                              ) extends MarketDataStrategyResponse{
    override def buildSignalFinderRequest(): signals.SignalFinderRequest = OpenGapRequest(signalInputs = signalInputs)
  }
}
