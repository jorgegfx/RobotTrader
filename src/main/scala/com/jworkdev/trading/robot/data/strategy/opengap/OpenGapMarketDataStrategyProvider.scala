package com.jworkdev.trading.robot.data.strategy.opengap

import com.jworkdev.trading.robot.data.strategy.MarketDataStrategyProvider
import com.jworkdev.trading.robot.market.data.{MarketDataProvider, SnapshotInterval}

import java.time.ZoneId
import java.time.format.DateTimeFormatter
import scala.util.Try

class OpenGapMarketDataStrategyProvider
    extends MarketDataStrategyProvider[
      OpenGapMarketDataStrategyRequest,
      OpenGapMarketDataStrategyResponse
    ]:
  private val DATE_PATTERN_FORMAT = "yyyy-MM-dd"
  private val dateFormatter = DateTimeFormatter
    .ofPattern(DATE_PATTERN_FORMAT)
    .withZone(ZoneId.systemDefault())
  private val marketDataProvider = MarketDataProvider()

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
        val priceMap = prices
          .map(price => (price, dateFormatter.format(price.snapshotTime)))
          .groupBy(_._2).map {
            case (key,value) => (key, value.map(_._1))
          }
        val res = priceMap.keys.toList.sorted
          .sliding(2)
          .map { case Seq(previous, current) =>
            (previous, current)
          }
          .flatMap { case (previous: String, current: String) =>
            val avgVol =
              if priceMap(current).nonEmpty then priceMap(current).map(_.volume).sum / priceMap(current).size
              else 0
            for
              closingPrice <- priceMap(previous).lastOption.map(_.close)
              openingPrice <- priceMap(current).headOption.map(_.open)
              currentPrices <- priceMap.get(current)
            yield OpenGapSignalInput(
              closingPrice = closingPrice,
              openingPrice = openingPrice,
              volumeAvg = avgVol,
              currentPrices = currentPrices
            )
          }
        OpenGapMarketDataStrategyResponse(signalInputs = res.toList)
      }

object OpenGapMarketDataStrategyProvider:
  def apply(): OpenGapMarketDataStrategyProvider = new OpenGapMarketDataStrategyProvider()
