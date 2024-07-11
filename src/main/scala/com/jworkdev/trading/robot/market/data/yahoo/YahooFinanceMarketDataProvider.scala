package com.jworkdev.trading.robot.market.data.yahoo

import com.fasterxml.jackson.databind.{DeserializationFeature, JsonNode, ObjectMapper}
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.jworkdev.trading.robot.market
import com.jworkdev.trading.robot.market.data.{MarketDataProvider, SnapshotFrequency, SnapshotInterval, StockPrice}
import com.jworkdev.trading.robot.market.data
import com.typesafe.scalalogging.Logger
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.{CloseableHttpClient, HttpClients}
import org.apache.http.util.EntityUtils

import java.time.{Instant, LocalDateTime, ZoneId, ZoneOffset}
import scala.util.{Failure, Try}

class YahooFinanceMarketDataProvider
    extends MarketDataProvider:

  import scala.jdk.CollectionConverters.*

  val baseUrl = "https://query1.finance.yahoo.com/v8/finance/chart"
  val client: CloseableHttpClient = HttpClients.createDefault()
  val mapper: ObjectMapper = new ObjectMapper()
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    .registerModule(DefaultScalaModule)

  private val logger = Logger(
    classOf[YahooFinanceMarketDataProvider]
  )

  private def parse(symbol: String, response: JsonNode): List[StockPrice] =
    val res = response.get("chart").get("result").elements().next()
    val timestamps = res.get("timestamp")
    val quotes =
      res.get("indicators").get("quote").elements().asScala.toList.head
    val openPrices = quotes.get("open").elements().asScala.toList
    val closePrices = quotes.get("close").elements().asScala.toList
    val highPrices = quotes.get("high").elements().asScala.toList
    val lowPrices = quotes.get("low").elements().asScala.toList
    val volumes = quotes.get("volume").elements().asScala.toList
    val timeStampsList = timestamps.elements().asScala.toList.map(_.asLong())
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
    if(responseCode != 200)
      Failure(new IllegalStateException(s"Invalid response $responseCode"))
    else 
      val entity = response.getEntity
      val responseString = EntityUtils.toString(entity)
      val json = mapper.readTree(responseString)
      Try(parse(symbol = symbol, response = json))

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

  def getQuotes(
      symbol: String,
      frequency: SnapshotFrequency
  ): Try[List[StockPrice]] = ???

object YahooFinanceMarketDataProvider:
  def apply(): YahooFinanceMarketDataProvider =
    new YahooFinanceMarketDataProvider()
