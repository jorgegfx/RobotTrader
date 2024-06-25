package com.jworkdev.trading.robot

import java.time.Instant
import scala.util.Try

package object data:
  case class StockPrice(
      symbol: String,
      open: Double,
      close: Double,
      high: Double,
      low: Double,
      volume: Long,
      snapshotTime: Instant
  ) {}

  enum StockQuoteInterval:
    case OneMinute, FiveMinutes, FifteenMinutes, ThirtyMinutes, SixtyMinutes

  enum StockQuoteFrequency:
    case Daily, Weekly, Monthly

  trait FinancialIInstrumentDataProvider:
    def getIntradayQuotes(
        symbol: String,
        interval: StockQuoteInterval
    ): Try[List[StockPrice]]
    def getQuotes(
        symbol: String,
        frequency: StockQuoteFrequency
    ): Try[List[StockPrice]]
