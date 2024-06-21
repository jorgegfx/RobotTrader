package com.jworkdev.trading.robot.data.alphavantage

import com.jworkdev.trading.robot.data.{StockQuote, StockQuoteFrequency, StockQuoteInterval, StockQuotesDataProvider}
import org.json.JSONObject

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.time.{LocalDateTime, ZoneId}
import java.time.format.DateTimeFormatter
import scala.util.{Failure, Try}

class AlphaVantageQuotesDataProvider extends StockQuotesDataProvider:
  import scala.jdk.CollectionConverters.*
  private val baseUrl = "https://www.alphavantage.co/query"
  private val apiKey = "TMNOJBPZ86A9UL98"
  private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

  private def fetchResponse(url: String):Try[List[StockQuote]] =
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

  private def parseStockTimeSeries(jsonString: String): Try[List[StockQuote]] =
    Try {
      val json = new JSONObject(jsonString)
      val keyMap = json.keys().asScala.zipWithIndex.toMap
      val metaData = json.getJSONObject("Meta Data")
      val symbol = metaData.getString("2. Symbol")
      val dataField = keyMap.filter(key=> !"Meta Data".equals(key)).keys.toList.head
      val timeSeries =
        json.getJSONObject(dataField) // Adjust key based on interval
      val iterator = timeSeries.keys()

      Iterator
        .continually {
          if iterator.hasNext then
            val date = iterator.next()
            val data = timeSeries.getJSONObject(date)
            val open = data.getDouble("1. open")
            val close = data.getDouble("4. close")
            val high = data.getDouble("2. high")
            val low = data.getDouble("3. low")
            val dateTime = LocalDateTime.parse(date, formatter)
            val zonedDateTime = dateTime.atZone(ZoneId.systemDefault())
            val snapshotTime = zonedDateTime.toInstant
            StockQuote(
              symbol = symbol,
              company = "",
              open = open,
              close = close,
              high = high,
              low = low,
              snapshotTime = snapshotTime
            )
          else throw new NoSuchElementException
        }
        .takeWhile(_ => iterator.hasNext)
        .toList
    }

  def getIntradayQuotes(
      symbol: String,
      interval: StockQuoteInterval
  ): Try[List[StockQuote]] = {
    val function = "TIME_SERIES_INTRADAY"
    val strInterval = interval match
      case StockQuoteInterval.OneMinute => "1min"
      case StockQuoteInterval.FiveMinutes => "5min"
      case StockQuoteInterval.FifteenMinutes => "15min"
      case StockQuoteInterval.ThirtyMinutes => "30min"
      case StockQuoteInterval.SixtyMinutes => "60min"
    val url =
      s"$baseUrl?function=$function&symbol=$symbol&interval=$strInterval&apikey=$apiKey"
    fetchResponse(url = url)
  }

  def getQuotes(
      symbol: String,
      frequency: StockQuoteFrequency
  ): Try[List[StockQuote]] = ???
