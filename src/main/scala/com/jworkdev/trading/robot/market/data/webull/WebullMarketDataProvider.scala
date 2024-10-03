package com.jworkdev.trading.robot.market.data.webull

import com.jworkdev.trading.robot.market.data.{MarketDataProvider, SnapshotFrequency, SnapshotInterval, StockPrice}
import com.webull.openapi.common.Region
import com.webull.openapi.quotes.api.QuotesApiClient

import scala.util.Try
import scala.util.Using

class WebullMarketDataProvider(val appKey: String, val appSecret: String) extends MarketDataProvider:
  def getCurrentMarketPriceQuote(symbol: String): Try[Double] = {
    Using (QuotesApiClient.builder().appKey(appKey)
      .appSecret(appSecret)
      .regionId(Region.us.name())
      .build()){ quotesApiClient =>
      val bars = quotesApiClient.getBars(symbol, "US_STOCK", "M1")
      bars.getLast.getClose.toDouble
    }
  }

  def getIntradayQuotes(
      symbol: String,
      interval: SnapshotInterval
  ): Try[List[StockPrice]] = ???

  def getIntradayQuotesDaysRange(
      symbol: String,
      interval: SnapshotInterval,
      daysRange: Int
  ): Try[List[StockPrice]] = ???

  def getQuotes(
      symbol: String,
      frequency: SnapshotFrequency
  ): Try[List[StockPrice]] = ???
