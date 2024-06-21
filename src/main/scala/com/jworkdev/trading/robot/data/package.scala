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

  trait StockQuotesDataProvider {
    def getCurrentInfo(symbol: String): Try[StockQuote]

    def getHistory(symbol: String, from: Instant, to: Instant): Try[List[StockQuote]]

    def release(): Unit
  }
}
