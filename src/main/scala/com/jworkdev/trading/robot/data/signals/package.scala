package com.jworkdev.trading.robot.data

import com.jworkdev.trading.robot.data.strategy.opengap.OpenGapSignalInput
import com.jworkdev.trading.robot.market.data.StockPrice

package object signals {

  import java.time.Instant

  enum SignalType:
    case Buy, Sell

  case class Signal(date: Instant, `type`: SignalType, stockPrice: StockPrice)

  trait SignalFinder[RequestType] {
    def find(request: RequestType): List[Signal]
  }

  sealed class SignalFinderRequest
  case class MovingAverageRequest(stockPrices: List[StockPrice]) extends SignalFinderRequest
  case class OpenGapRequest(signalInputs: List[OpenGapSignalInput]) extends SignalFinderRequest
  case class RelativeStrengthIndexRequest(stockPrices: List[StockPrice]) extends SignalFinderRequest
  case class MACDRequest(stockPrices: List[StockPrice], validate: Boolean) extends SignalFinderRequest
}
