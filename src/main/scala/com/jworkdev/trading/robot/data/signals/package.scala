package com.jworkdev.trading.robot.data

package object signals {

  import java.time.Instant

  enum SignalType:
    case Buy, Sell

  case class Signal(date: Instant, `type`: SignalType)

  trait SignalFinder[RequestType] {
    def find(request: RequestType): List[Signal]
  }

  sealed class SignalFinderRequest
  case class MovingAverageRequest(stockQuotes: List[StockQuote]) extends SignalFinderRequest
}
