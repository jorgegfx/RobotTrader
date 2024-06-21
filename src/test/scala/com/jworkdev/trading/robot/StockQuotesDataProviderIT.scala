package com.jworkdev.trading.robot

import com.jworkdev.trading.robot.data.ib.IBStockQuotesDataProvider


object StockQuotesDataProviderIT extends App {
  val stockQuotesDataProvider = IBStockQuotesDataProvider("localhost",4002)
  val stockQuote = stockQuotesDataProvider.getCurrentInfo("AAPL")
  try Thread.sleep(100000)
  catch {
    case e: InterruptedException =>
      e.printStackTrace()
  }
  println(s"res: $stockQuote")

  //stockQuotesDataProvider.release()
}
