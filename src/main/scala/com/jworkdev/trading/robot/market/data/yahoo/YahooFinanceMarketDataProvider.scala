package com.jworkdev.trading.robot.market.data.yahoo

import com.fasterxml.jackson.databind.{DeserializationFeature, JsonNode, ObjectMapper}
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.jworkdev.trading.robot.market
import com.jworkdev.trading.robot.market.data.{MarketDataProvider, SnapshotFrequency, SnapshotInterval, StockPrice}
import com.jworkdev.trading.robot.market.data
import com.typesafe.scalalogging.Logger
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.{CloseableHttpClient, HttpClients}
import org.apache.http.util.EntityUtils

import java.time.{Instant, LocalDateTime, ZoneId, ZoneOffset}
import scala.util.{Failure, Success, Try, Using}

class YahooFinanceMarketDataProvider extends MarketDataProvider:

  import scala.jdk.CollectionConverters.*

  val baseUrl = "https://query1.finance.yahoo.com/v8/finance/chart"
  val mapper: ObjectMapper = new ObjectMapper()
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    .registerModule(DefaultScalaModule)

  // Define timeout values in milliseconds
  private val connectionTimeout = 5000
  private val socketTimeout = 5000
  private val requestTimeout = 5000

  // Create a RequestConfig with the timeout settings
  private val requestConfig = RequestConfig.custom()
    .setConnectTimeout(connectionTimeout)
    .setSocketTimeout(socketTimeout)
    .setConnectionRequestTimeout(requestTimeout)
    .build()

  private val logger = Logger(
    classOf[YahooFinanceMarketDataProvider]
  )

  private def fetchResponse(symbol: String): Try[Double] =
    val url = s"$baseUrl/$symbol"
    Using(HttpClients.custom().setDefaultRequestConfig(requestConfig).build()){ httpClient =>
      val request = new HttpGet(url)
      Using(httpClient.execute(request)){ response=>
        val responseCode = response.getStatusLine.getStatusCode
        if responseCode != 200 then
          Failure(new IllegalStateException(s"Invalid response $responseCode"))
        else
          val entity = response.getEntity
          val responseString = EntityUtils.toString(entity)
          Try(mapper.readTree(responseString)).map(json =>
            for
              chart <- Option(json.get("chart"))
              result <- Option(chart.get("result")).map(_.elements()).flatMap(_.asScala.toList.headOption)
              meta <- Option(result.get("meta"))
              regularMarketPrice <- Option(meta.get("regularMarketPrice")).map(_.asDouble())
            yield regularMarketPrice
          ) flatMap {
            case Some(value) => Success(value)
            case None => Failure(new IllegalStateException("No Price found!"))
          }
      }.flatten
    }.flatten

  override def getCurrentQuote(symbol: String): Try[Double] =
    fetchResponse(symbol = symbol)

  override def getIntradayQuotes(
      symbol: String,
      interval: SnapshotInterval
  ): Try[List[StockPrice]] =
    fetchResponse(symbol = symbol, interval = interval)

  override def getIntradayQuotesDaysRange(
      symbol: String,
      interval: SnapshotInterval,
      daysRange: Int
  ): Try[List[StockPrice]] =
    fetchResponse(symbol = symbol, interval = interval, daysRange = daysRange)

  private def fetchResponse(
      symbol: String,
      interval: SnapshotInterval,
      daysRange: Int = 1
  ): Try[List[StockPrice]] =
    val internalParam = interval match
      case data.SnapshotInterval.OneMinute      => "1m"
      case data.SnapshotInterval.FiveMinutes    => "5m"
      case data.SnapshotInterval.FifteenMinutes => "15m"
      case data.SnapshotInterval.ThirtyMinutes  => "30m"
      case data.SnapshotInterval.SixtyMinutes   => "60m"
    val client: CloseableHttpClient = HttpClients.createDefault()
    val url = s"$baseUrl/$symbol?interval=$internalParam&range=${daysRange}d"
    logger.info(s"fetching url :$url ...")
    val request = new HttpGet(url)
    val response = client.execute(request)
    val responseCode = response.getStatusLine.getStatusCode
    if responseCode != 200 then Failure(new IllegalStateException(s"Invalid response $responseCode"))
    else
      val entity = response.getEntity
      val responseString = EntityUtils.toString(entity)
      val json = mapper.readTree(responseString)
      Try(parse(symbol = symbol, response = json))

  private def parse(symbol: String, response: JsonNode): List[StockPrice] =
    val res = for
      chart <- Option(response.get("chart"))
      result <- Option(chart.get("result"))
      elements <- Option(result.elements())
      res <- elements.asScala.toList.headOption
    yield res
    res match
      case Some(value) => parseResponse(symbol = symbol, res = value)
      case None        => List.empty

  private def parseResponse(symbol: String, res: JsonNode): List[StockPrice] =
    val result = for
      timestamps <- Option(res.get("timestamp"))
      timestampsList <- Option(timestamps.elements()).map(_.asScala.toList)
      indicators <- Option(res.get("indicators"))
      quote <- Option(indicators.get("quote"))
      elements <- Option(quote.elements()).map(_.asScala.toList)
      quotes <- elements.headOption
    yield (timestampsList, quotes)
    result match
      case Some(value) => parseResponse(symbol = symbol, timestamps = value._1, quotes = value._2)
      case None        => List.empty

  private def parseResponse(symbol: String, timestamps: List[JsonNode], quotes: JsonNode): List[StockPrice] =
    val result = for
      openPrices <- Option(quotes.get("open")).map(_.elements()).map(_.asScala.toList)
      closePrices <- Option(quotes.get("close")).map(_.elements()).map(_.asScala.toList)
      highPrices <- Option(quotes.get("high")).map(_.elements()).map(_.asScala.toList)
      lowPrices <- Option(quotes.get("low")).map(_.elements()).map(_.asScala.toList)
      volumes <- Option(quotes.get("volume")).map(_.elements()).map(_.asScala.toList)
    yield (openPrices, closePrices, highPrices, lowPrices, volumes)
    result match
      case Some(value) =>
        parseResponse(
          symbol = symbol,
          timestamps = timestamps,
          openPrices = value._1,
          closePrices = value._2,
          highPrices = value._3,
          lowPrices = value._4,
          volumes = value._5
        )
      case None => List.empty

  private def parseResponse(
      symbol: String,
      timestamps: List[JsonNode],
      openPrices: List[JsonNode],
      closePrices: List[JsonNode],
      highPrices: List[JsonNode],
      lowPrices: List[JsonNode],
      volumes: List[JsonNode]
  ): List[StockPrice] =
    val timeStampsList = timestamps.map(_.asLong())
    val zoneId = ZoneId.of("America/New_York")
    timeStampsList.zipWithIndex.map { case (timestamp: Long, index: Int) =>
      val closePrice = closePrices(index).asDouble()
      val openPrice = openPrices(index).asDouble()
      val highPrice = highPrices(index).asDouble()
      val lowPrice = lowPrices(index).asDouble()
      val volume = volumes(index).asLong()
      val dateTime = LocalDateTime.ofInstant(
        Instant.ofEpochSecond(timestamp),
        zoneId
      )
      val snapshotTime = dateTime.toInstant(ZoneOffset.UTC)
      StockPrice(
        symbol = symbol,
        open = openPrice,
        close = closePrice,
        high = highPrice,
        low = lowPrice,
        volume = volume,
        snapshotTime = snapshotTime
      )
    }

  def getQuotes(
      symbol: String,
      frequency: SnapshotFrequency
  ): Try[List[StockPrice]] = ???

object YahooFinanceMarketDataProvider:
  def apply(): YahooFinanceMarketDataProvider =
    new YahooFinanceMarketDataProvider()
