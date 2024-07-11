package com.jworkdev.trading.robot.market.data.alphavantage

import com.github.tototoshi.csv.*
import com.jworkdev.trading.robot.domain
import com.jworkdev.trading.robot.market.data.ExchangeDataProvider
import com.typesafe.scalalogging.Logger
import sttp.client3.*
import zio.{Task, ZIO}

import java.nio.file.{Files, Paths}
import scala.io.Source
import scala.util.Using

class AlphaVantageExchangeDataProvider extends ExchangeDataProvider:
  private val apiKey = "TMNOJBPZ86A9UL98"
  private val cacheFile = "input/exchange_listing.csv"
  private val logger = Logger(classOf[AlphaVantageExchangeDataProvider])
  override def findAllSymbols(exchange: String, finInstrumentType: domain.FinInstrumentType): Task[List[String]] =
    val path = Paths.get(cacheFile)
    if(Files.exists(path) && Files.isRegularFile(path))
      logger.info(s"Reading from cache $cacheFile ...")
      ZIO.attemptBlockingIO(getCacheCSVData).map(mapCsvData).map(_.map(_.symbol).toList)
    else fetch(exchange, finInstrumentType)

  private def getCacheCSVData: String = {
    Using(Source.fromFile(cacheFile)) { source =>
      source.getLines().mkString("\n")
    }.fold(ex=>
      logger.error(s"Error reading $cacheFile !",ex)
      "", input => input)
  }

  private def fetch(exchange: String, finInstrumentType: domain.FinInstrumentType): Task[List[String]] =
    val url = s"https://www.alphavantage.co/query?function=LISTING_STATUS&apikey=$apiKey"
    for {
      _ <- ZIO.log(s"Fetching Symbol exchange: $exchange ...")
      stocks <- ZIO.attemptBlockingIO(fetchStockListings(url))
    } yield (filterStocksByExchange(stocks, exchange, finInstrumentType).map(_.symbol).toList)

  private def mapCsvData(csvData: String): Seq[StockListing] =
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

  private def fetchStockListings(url: String): Seq[StockListing] =
    val backend = HttpURLConnectionBackend()
    val response = basicRequest.get(uri"$url").send(backend)

    response.body match
      case Right(csvData) => mapCsvData(csvData = csvData)
      case Left(error) =>
        throw new RuntimeException(s"Failed to fetch data: $error")

  private def filterStocksByExchange(stocks: Seq[StockListing], exchange: String, finInstrumentType: domain.FinInstrumentType): Seq[StockListing] =
    stocks.filter(stock=> stock.exchange == exchange && stock.assetType == finInstrumentType.toString)

  private case class StockListing(symbol: String, name: String, exchange: String, assetType: String)
