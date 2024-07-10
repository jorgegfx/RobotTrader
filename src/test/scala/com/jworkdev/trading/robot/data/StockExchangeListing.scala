package com.jworkdev.trading.robot.data

import sttp.client3._
import com.github.tototoshi.csv._
import scala.io.Source
import scala.util.Using

object StockExchangeListing {
  def main(args: Array[String]): Unit = {
    val apiKey = "TMNOJBPZ86A9UL98"
    val exchange = "NASDAQ" // Example: NASDAQ, NYSE
    val url = s"https://www.alphavantage.co/query?function=LISTING_STATUS&apikey=$apiKey"

    val stockListings = fetchStockListings(url)
    val filteredStocks = filterStocksByExchange(stockListings, exchange)

    filteredStocks.foreach(println)
  }

  case class StockListing(symbol: String, name: String, exchange: String, assetType: String)

  def fetchStockListings(url: String): Seq[StockListing] = {
    val backend = HttpURLConnectionBackend()
    val response = basicRequest.get(uri"$url").send(backend)

    response.body match {
      case Right(csvData) =>
        Using.resource(CSVReader.open(Source.fromString(csvData))) { reader =>
          reader.allWithHeaders().map(row => StockListing(
            symbol = row("symbol"),
            name = row("name"),
            exchange = row("exchange"),
            assetType = row("assetType")
          ))
        }
      case Left(error) =>
        throw new RuntimeException(s"Failed to fetch data: $error")
    }
  }

  def filterStocksByExchange(stocks: Seq[StockListing], exchange: String): Seq[StockListing] = {
    stocks.filter(_.exchange == exchange)
  }
}

