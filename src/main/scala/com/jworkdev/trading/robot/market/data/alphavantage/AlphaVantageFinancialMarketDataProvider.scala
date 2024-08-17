package com.jworkdev.trading.robot.market.data.alphavantage

import com.jworkdev.trading.robot.market.data
import com.jworkdev.trading.robot.market.data.{MarketDataProvider, StockPrice, SnapshotFrequency, SnapshotInterval}
import com.typesafe.scalalogging.Logger
import org.json.JSONObject

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.time.{LocalDateTime, ZoneId}
import java.time.format.DateTimeFormatter
import scala.util.{Failure, Try}

class AlphaVantageFinancialMarketDataProvider extends MarketDataProvider:
  private val logger = Logger(classOf[AlphaVantageFinancialMarketDataProvider])
  import scala.jdk.CollectionConverters.*
  private val baseUrl = "https://www.alphavantage.co/query"
  private val apiKey = "TMNOJBPZ86A9UL98"
  private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

  private def fetchResponse(url: String):Try[List[StockPrice]] =
    logger.info(s"fetching :$url")
    val request = HttpRequest
      .newBuilder()
      .uri(URI.create(url))
      .build()

    val response = HttpClient
      .newHttpClient()
      .send(request, HttpResponse.BodyHandlers.ofString())

    if response.statusCode() == 200 then parseStockTimeSeries(response.body())
    else
      Failure(
        new RuntimeException(
          s"Failed to fetch data: ${response.statusCode()} ${response.body()}"
        )
      )

  private def parseStockTimeSeries(jsonString: String): Try[List[StockPrice]] =
    Try {
      val json = new JSONObject(jsonString)
      val keyMap = json.keys().asScala.zipWithIndex.toMap
      val metaData = json.getJSONObject("Meta Data")
      val symbol = metaData.getString("2. Symbol")
      val dataField = keyMap.filter(key=> !"Meta Data".equals(key)).keys.toList.head
      val timeSeries =
        json.getJSONObject(dataField) // Adjust key based on interval
      val entryKeys = timeSeries.keys().asScala.toList
      val entries = entryKeys.map(entry => {
        val date = entry
        val data = timeSeries.getJSONObject(date)
        val open = data.getDouble("1. open")
        val close = data.getDouble("4. close")
        val high = data.getDouble("2. high")
        val low = data.getDouble("3. low")
        val volume = data.getLong("5. volume")
        val dateTime = LocalDateTime.parse(date, formatter)
        val zonedDateTime = dateTime.atZone(ZoneId.systemDefault())
        StockPrice(
          symbol = symbol,
          open = open,
          close = close,
          high = high,
          low = low,
          volume = volume,
          snapshotTime = zonedDateTime
        )
      })
      entries.sortBy(_.snapshotTime)
    }

  override def getIntradayQuotes(
      symbol: String,
      interval: SnapshotInterval
  ): Try[List[StockPrice]] = {
    val function = "TIME_SERIES_INTRADAY"
    val strInterval = interval match
      case SnapshotInterval.OneMinute => "1min"
      case SnapshotInterval.FiveMinutes => "5min"
      case SnapshotInterval.FifteenMinutes => "15min"
      case SnapshotInterval.ThirtyMinutes => "30min"
      case SnapshotInterval.SixtyMinutes => "60min"
    val url =
      s"$baseUrl?function=$function&symbol=$symbol&interval=$strInterval&apikey=$apiKey&outputsize=full"
    fetchResponse(url = url)
  }

  override def getCurrentMarketPriceQuote(symbol: String): Try[Double] = ???

  override def getIntradayQuotesDaysRange(symbol: String, interval: SnapshotInterval, daysRange: Int): Try[List[StockPrice]] = ???
  
  override def getQuotes(
      symbol: String,
      frequency: SnapshotFrequency
  ): Try[List[StockPrice]] = {
    val function = frequency match
      case SnapshotFrequency.Daily => "TIME_SERIES_DAILY"
      case SnapshotFrequency.Weekly => "TIME_SERIES_WEEKLY"
      case SnapshotFrequency.Monthly => "TIME_SERIES_MONTHLY"
    val url =
      s"$baseUrl?function=$function&symbol=$symbol&apikey=$apiKey&outputsize=full"
    fetchResponse(url = url)
  }
