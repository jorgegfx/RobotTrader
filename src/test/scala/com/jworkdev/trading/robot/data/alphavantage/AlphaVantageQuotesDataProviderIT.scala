package com.jworkdev.trading.robot.data.alphavantage

import com.jworkdev.trading.robot.data.StockQuoteInterval.FiveMinutes

object AlphaVantageQuotesDataProviderIT extends App:
  val provider = AlphaVantageFinancialInstrumentDataProvider()
  val quotes = provider.getIntradayQuotes("AAPL",FiveMinutes)
  quotes.foreach(_.foreach(println))

