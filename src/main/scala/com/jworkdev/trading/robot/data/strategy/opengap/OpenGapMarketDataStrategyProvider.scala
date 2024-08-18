package com.jworkdev.trading.robot.data.strategy.opengap

import com.jworkdev.trading.robot.data.strategy.MarketDataStrategyProvider
import com.jworkdev.trading.robot.domain.groupPricesByDate
import com.jworkdev.trading.robot.market.data.{MarketDataProvider, SnapshotInterval, StockPrice}
import com.jworkdev.trading.robot.time.InstantExtensions.toLocalDateTime
import com.typesafe.scalalogging.Logger

import java.time.LocalDate
import scala.util.Try

class OpenGapMarketDataStrategyProvider(private val marketDataProvider: MarketDataProvider)
    extends MarketDataStrategyProvider[
      OpenGapMarketDataStrategyRequest,
      OpenGapMarketDataStrategyResponse
    ]:
  private val logger = Logger(classOf[OpenGapMarketDataStrategyProvider])

  def buildFromPrices(prices: List[StockPrice]): OpenGapMarketDataStrategyResponse =
    val priceMap = groupPricesByDate(prices = prices)
    val signalInputs =
      if priceMap.nonEmpty && priceMap.size > 1 then buildSignalInputs(priceMap = priceMap)
      else List.empty
    OpenGapMarketDataStrategyResponse(signalInputs = signalInputs)

  override def provide(
      request: OpenGapMarketDataStrategyRequest
  ): Try[OpenGapMarketDataStrategyResponse] =
    marketDataProvider
      .getIntradayQuotesDaysRange(
        symbol = request.symbol,
        interval = SnapshotInterval.SixtyMinutes,
        daysRange = request.signalCount + 1
      )
      .map { this.buildFromPrices }

  private def buildSignalInputs(priceMap: Map[LocalDate, List[StockPrice]]): List[OpenGapSignalInput] =
    priceMap.keys.toList.sorted
      .sliding(2)
      .flatMap {
        case Seq(previous, current) =>
          Some(previous, current)
        case _ => None
      }
      .flatMap { case (previous: LocalDate, current: LocalDate) =>
        val currentPrices = priceMap(current).sortBy(_.snapshotTime)
        val previousPrices = priceMap(previous).sortBy(_.snapshotTime)
        val avgVol = calculateAverageVolume(prices = currentPrices)
        for
          closingPrice <- getLastPrice(prices = previousPrices).map(_.close)
          openingPrice <- getFirstPrice(prices = currentPrices).map(_.open)
          tradingDateTime <- currentPrices.lastOption.map(_.snapshotTime)
        yield OpenGapSignalInput(
          tradingDateTime = tradingDateTime,
          closingPrice = closingPrice,
          openingPrice = openingPrice,
          volumeAvg = avgVol,
          currentPrices = currentPrices
        )
      }
      .toList

  private def calculateAverageVolume(prices: List[StockPrice]): Double =
    if prices.nonEmpty then prices.map(_.volume).sum / prices.size
    else 0.0

  private def getLastPrice(prices: List[StockPrice]): Option[StockPrice] =
    prices.sortBy(_.snapshotTime).lastOption

  private def getFirstPrice(prices: List[StockPrice]): Option[StockPrice] =
    prices.sortBy(_.snapshotTime).headOption

object OpenGapMarketDataStrategyProvider:
  def apply(marketDataProvider: MarketDataProvider): OpenGapMarketDataStrategyProvider =
    new OpenGapMarketDataStrategyProvider(marketDataProvider)
