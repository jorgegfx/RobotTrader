package com.jworkdev.trading.robot.data.yahoo

import com.fasterxml.jackson.databind.{DeserializationFeature, JsonNode, ObjectMapper}
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.jworkdev.trading.robot.data
import com.jworkdev.trading.robot.data.{FinancialIInstrumentDataProvider, StockPrice, StockQuoteFrequency, StockQuoteInterval}
import com.typesafe.scalalogging.Logger
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.{CloseableHttpClient, HttpClients}
import org.apache.http.util.EntityUtils

import java.time.{Instant, LocalDateTime, ZoneId, ZoneOffset}
import scala.util.Try

class YahooFinanceFinancialInstrumentDataProvider
    extends FinancialIInstrumentDataProvider:

  import scala.jdk.CollectionConverters.*

  val baseUrl = "https://query1.finance.yahoo.com/v8/finance/chart"
  val client: CloseableHttpClient = HttpClients.createDefault()
  val mapper: ObjectMapper = new ObjectMapper()
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    .registerModule(DefaultScalaModule)

  private val logger = Logger(classOf[YahooFinanceFinancialInstrumentDataProvider])

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

  private def fetchResponse(symbol: String,interval: StockQuoteInterval): List[StockPrice] =
    val internalParam = interval match
      case data.StockQuoteInterval.OneMinute => "1m"
      case data.StockQuoteInterval.FiveMinutes => "5m"
      case data.StockQuoteInterval.FifteenMinutes => "15m"
      case data.StockQuoteInterval.ThirtyMinutes => "30m"
      case data.StockQuoteInterval.SixtyMinutes => "60m"
    val client: CloseableHttpClient = HttpClients.createDefault()
    val url = s"$baseUrl/$symbol?interval=$internalParam&range=1d"
    logger.info(s"fetching url :$url ...")
    val request = new HttpGet(url)
    val response = client.execute(request)
    val entity = response.getEntity
    val responseString = EntityUtils.toString(entity)
    val json = mapper.readTree(responseString)
    parse(symbol = symbol, response = json)

  override def getIntradayQuotes(
      symbol: String,
      interval: StockQuoteInterval
  ): Try[List[StockPrice]] =
    Try(fetchResponse(symbol = symbol,interval: StockQuoteInterval))

  def getQuotes(
      symbol: String,
      frequency: StockQuoteFrequency
  ): Try[List[StockPrice]] = ???


object YahooFinanceFinancialInstrumentDataProvider{
  def apply(): YahooFinanceFinancialInstrumentDataProvider = new YahooFinanceFinancialInstrumentDataProvider()
}