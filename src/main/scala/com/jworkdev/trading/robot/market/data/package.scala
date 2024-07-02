package com.jworkdev.trading.robot.market

import com.jworkdev.trading.robot.market.data.yahoo.YahooFinanceMarketDataProvider

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

  enum SnapshotInterval:
    case OneMinute, FiveMinutes, FifteenMinutes, ThirtyMinutes, SixtyMinutes

  enum SnapshotFrequency:
    case Daily, Weekly, Monthly

  trait MarketDataProvider:
    def getIntradayQuotes(
        symbol: String,
        interval: SnapshotInterval
    ): Try[List[StockPrice]]

    def getIntradayQuotesDaysRange(
        symbol: String,
        interval: SnapshotInterval,
        daysRange: Int
    ): Try[List[StockPrice]]

    def getQuotes(
        symbol: String,
        frequency: SnapshotFrequency
    ): Try[List[StockPrice]]

  object MarketDataProvider:
    def apply(): MarketDataProvider =
      new YahooFinanceMarketDataProvider()
