package market.data.alphavantage

import com.jworkdev.trading.robot.market

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import scala.util.{Failure, Success, Try}
import org.json.JSONObject

object AlphaVantageAPI:
  private val baseUrl = "https://www.alphavantage.co/query"
  private val apiKey =
    "TMNOJBPZ86A9UL98" // Replace with your AlphaVantage API key

  def getStockTimeSeries(
      symbol: String,
      interval: String
  ): Try[List[StockPrice]] =
    val function = "TIME_SERIES_INTRADAY"
    val url =
      s"$baseUrl?function=$function&symbol=$symbol&interval=$interval&apikey=$apiKey"

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
      val timeSeries =
        json.getJSONObject("Time Series (5min)") // Adjust key based on interval
      val iterator = timeSeries.keys()

      Iterator
        .continually {
          if iterator.hasNext then
            val date = iterator.next()
            val data = timeSeries.getJSONObject(date)
            StockPrice(
              date,
              data.getDouble("1. open"),
              data.getDouble("4. close"),
              data.getDouble("2. high"),
              data.getDouble("3. low")
            )
          else throw new NoSuchElementException
        }
        .takeWhile(_ => iterator.hasNext)
        .toList
    }

  case class StockPrice(
      date: String,
      open: Double,
      close: Double,
      high: Double,
      low: Double
  ):
    def average: Double = (high + low) / 2

object Main extends App:
  // Example usage:
  AlphaVantageAPI.getStockTimeSeries("AAPL", "5min") match
    case Success(prices) => prices.foreach(println)
    case Failure(exception) =>
      println(s"Failed to fetch data: ${exception.getMessage}")
