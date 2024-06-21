package com.jworkdev.trading.robot

import java.time.Instant
import scala.util.Try

package object data {
  case class StockQuote(symbol: String,
    company: String,
    open: Double,
    close: Double,
    high: Double,
    low: Double,  
    snapshotTime: Instant) {
  }

  enum StockQuoteInterval:
    case OneMinute, FiveMinutes, FifteenMinutes, ThirtyMinutes, SixtyMinutes

  enum StockQuoteFrequency:
    case Daily, Weekly, Monthly

  trait StockQuotesDataProvider {
    def getIntradayQuotes(symbol: String,
                          interval: StockQuoteInterval): Try[List[StockQuote]]
    def getQuotes(symbol: String,
                  frequency: StockQuoteFrequency): Try[List[StockQuote]]

  }
}
