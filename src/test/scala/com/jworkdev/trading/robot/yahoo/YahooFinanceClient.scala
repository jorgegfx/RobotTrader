package com.jworkdev.trading.robot.yahoo

import com.fasterxml.jackson.databind.{DeserializationFeature, JsonNode, ObjectMapper}
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.{CloseableHttpClient, HttpClients}
import org.apache.http.util.EntityUtils

import java.time.{Instant, LocalDateTime, ZoneId}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.*
import scala.concurrent.{Await, Future}

case class ChartResponse(chart: Chart)
case class Chart(result: List[ChartResult])
case class ChartResult(timestamp: List[Long], indicators: Indicators)
case class Indicators(quote: List[Quote])
case class Quote(close: List[Double])

object YahooFinanceClient {
  import scala.jdk.CollectionConverters.*
  val baseUrl = "https://query1.finance.yahoo.com/v8/finance/chart"
  val client: CloseableHttpClient = HttpClients.createDefault()
  val mapper: ObjectMapper = new ObjectMapper().
    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false).
    registerModule(DefaultScalaModule)

  def parse(response: JsonNode):List[(LocalDateTime,Double)] = {
    val res = response.get("chart").get("result").elements().next()
    val timestamps = res.get("timestamp")
    val quotes = res.get("indicators").get("quote").elements().asScala.toList.head
    val prices = quotes.get("close").elements().asScala.toList
    val timeStampsList = timestamps.elements().asScala.toList.map(_.asLong())
    timeStampsList.zipWithIndex.map{case (timestamp: Long, index: Int)=>
      val price = prices(index).asDouble()
      val dateTime = LocalDateTime.ofInstant(Instant.ofEpochSecond(timestamp), ZoneId.of("America/New_York"))
      (dateTime,price)
    }
  }

  def getMarketData(symbol: String): Future[List[(LocalDateTime,Double)]] = Future {
    val url = s"$baseUrl/$symbol?interval=5m&range=1d"
    val request = new HttpGet(url)
    val response = client.execute(request)
    val entity = response.getEntity
    val responseString = EntityUtils.toString(entity)
    val json = mapper.readTree(responseString)
    parse(json)
  }

  def printMarketData(symbol: String): Unit = {
    val future = getMarketData(symbol).map { values =>
      values.foreach { case (date: LocalDateTime, price: Double) =>
        println(s"Time: ${date}, Close Price: $price")
      }
    }.recover {
      case ex: Exception =>
        println(s"An error occurred: ${ex.getMessage}")
        ex.printStackTrace()
    }
    Await.ready(future, Duration.Inf)
  }

  def main(args: Array[String]): Unit = {
    val symbol = "AAPL"
    printMarketData(symbol)
    client.close()
  }
}

