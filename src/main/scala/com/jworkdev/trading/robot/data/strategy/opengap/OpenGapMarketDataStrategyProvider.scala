package com.jworkdev.trading.robot.data.strategy.opengap

import com.jworkdev.trading.robot.data.strategy.MarketDataStrategyProvider
import com.jworkdev.trading.robot.market.data.{MarketDataProvider, SnapshotInterval, StockPrice}

import java.time.ZoneId
import java.time.format.DateTimeFormatter
import scala.util.Try

class OpenGapMarketDataStrategyProvider(private val marketDataProvider: MarketDataProvider)
    extends MarketDataStrategyProvider[
      OpenGapMarketDataStrategyRequest,
      OpenGapMarketDataStrategyResponse
    ]:
  private val DATE_PATTERN_FORMAT = "yyyy-MM-dd"
  private val dateFormatter = DateTimeFormatter
    .ofPattern(DATE_PATTERN_FORMAT)
    .withZone(ZoneId.systemDefault())

  private def buildPriceMap(prices: List[StockPrice]): Map[String, List[StockPrice]] =
    prices
      .map(price => (price, dateFormatter.format(price.snapshotTime)))
      .groupBy(_._2).map {
        case (key, value) => (key, value.map(_._1))
      }

  private def calculateAverageVolume(prices: List[StockPrice]): Double =
    if prices.nonEmpty then
      prices.map(_.volume).sum / prices.size
    else 0.0

  private def getLastPrice(prices: List[StockPrice]): Option[StockPrice] =
    prices.sortBy(_.snapshotTime).lastOption

  private def getFirstPrice(prices: List[StockPrice]): Option[StockPrice] =
    prices.sortBy(_.snapshotTime).headOption

  private def buildSignalInputs(priceMap: Map[String, List[StockPrice]]): List[OpenGapSignalInput] =
    priceMap.keys.toList.sorted
      .sliding(2)
      .map { case Seq(previous, current) =>
        (previous, current)
      }
      .flatMap { case (previous: String, current: String) =>
        val avgVol = calculateAverageVolume(prices = priceMap(current))
        for
          closingPrice <- getLastPrice(prices = priceMap(previous)).map(_.close)
          openingPrice <- getFirstPrice(prices = priceMap(current)).map(_.open)
          currentPrices <- priceMap.get(current).map(_.sortBy(_.snapshotTime))
        yield OpenGapSignalInput(
          closingPrice = closingPrice,
          openingPrice = openingPrice,
          volumeAvg = avgVol,
          currentPrices = currentPrices
        )
      }.toList

  override def provide(
      request: OpenGapMarketDataStrategyRequest
  ): Try[OpenGapMarketDataStrategyResponse] =
    marketDataProvider
      .getIntradayQuotesDaysRange(
        symbol = request.symbol,
        interval = SnapshotInterval.SixtyMinutes,
        daysRange = request.signalCount + 1
      )
      .map { prices =>
        val priceMap = buildPriceMap(prices = prices)
        val signalInputs =
          if(priceMap.nonEmpty && priceMap.size>1)
            buildSignalInputs(priceMap = priceMap)
          else List.empty
        OpenGapMarketDataStrategyResponse(signalInputs = signalInputs)
      }

object OpenGapMarketDataStrategyProvider:
  def apply(marketDataProvider: MarketDataProvider): OpenGapMarketDataStrategyProvider =
    new OpenGapMarketDataStrategyProvider(marketDataProvider)
