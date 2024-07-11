package com.jworkdev.trading.robot.market.data.alphavantage

import com.github.tototoshi.csv.*
import com.jworkdev.trading.robot.domain
import com.jworkdev.trading.robot.market.data.ExchangeDataProvider
import sttp.client3.*
import zio.{Task, ZIO}

import scala.io.Source
import scala.util.Using

class AlphaVantageExchangeDataProvider extends ExchangeDataProvider:
  private val apiKey = "TMNOJBPZ86A9UL98"

  override def findAllSymbols(exchange: String, finInstrumentType: domain.FinInstrumentType): Task[List[String]] =
    val url = s"https://www.alphavantage.co/query?function=LISTING_STATUS&apikey=$apiKey"
    for{
      _ <- ZIO.log(s"Fetching Symbol exchange: $exchange ...")
      stocks <- ZIO.attemptBlockingIO(fetchStockListings(url))
    } yield(filterStocksByExchange(stocks, exchange, finInstrumentType).map(_.symbol).toList)

  private def fetchStockListings(url: String): Seq[StockListing] =
    val backend = HttpURLConnectionBackend()
    val response = basicRequest.get(uri"$url").send(backend)

    response.body match
      case Right(csvData) =>
        Using.resource(CSVReader.open(Source.fromString(csvData))) { reader =>
          reader
            .allWithHeaders()
            .map(row =>
              StockListing(
                symbol = row("symbol"),
                name = row("name"),
                exchange = row("exchange"),
                assetType = row("assetType")
              )
            )
        }
      case Left(error) =>
        throw new RuntimeException(s"Failed to fetch data: $error")

  private def filterStocksByExchange(stocks: Seq[StockListing], exchange: String, finInstrumentType: domain.FinInstrumentType): Seq[StockListing] =
    stocks.filter(stock=> stock.exchange == exchange && stock.assetType == finInstrumentType.toString)

  private case class StockListing(symbol: String, name: String, exchange: String, assetType: String)
