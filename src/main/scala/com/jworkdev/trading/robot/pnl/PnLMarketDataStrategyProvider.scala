package com.jworkdev.trading.robot.pnl

import com.jworkdev.trading.robot.data.signals
import com.jworkdev.trading.robot.data.signals.SignalFinderRequest
import com.jworkdev.trading.robot.data.strategy.opengap.{
  OpenGapMarketDataStrategyProvider,
  OpenGapMarketDataStrategyResponse,
  OpenGapSignalInput
}
import com.jworkdev.trading.robot.data.strategy.{MarketDataStrategyProvider, MarketDataStrategyRequestFactory}
import com.jworkdev.trading.robot.domain.groupPricesByDate
import com.jworkdev.trading.robot.market.data.{MarketDataProvider, SnapshotInterval, StockPrice}

import java.time.{LocalDate, LocalDateTime, LocalTime}
import scala.util.Success

trait PnLMarketDataStrategyProvider:
  def provide(symbol: String, daysCount: Int, exchangeCloseTime: LocalTime): List[MarketDataEntry]

class OpenGapPnLMarketDataStrategyProvider extends PnLMarketDataStrategyProvider:
  private val marketDataStrategyRequestFactory = MarketDataStrategyRequestFactory()
  private val marketDataStrategyProvider = MarketDataStrategyProvider()
  private val marketDataProvider = MarketDataProvider()
  private val openGapMarketDataStrategyProvider = new OpenGapMarketDataStrategyProvider(marketDataProvider)

  def provide(symbol: String, daysCount: Int, exchangeCloseTime: LocalTime): List[MarketDataEntry] =
    marketDataProvider
      .getIntradayQuotesDaysRange(
        symbol = symbol,
        interval = SnapshotInterval.SixtyMinutes,
        daysRange = daysCount + 1
      )
      .map(groupPricesByDate)
      .map(priceMap => provide(priceMap = priceMap, exchangeCloseTime = exchangeCloseTime))
      .getOrElse(List.empty)

  def provide(priceMap: Map[LocalDate, List[StockPrice]], exchangeCloseTime: LocalTime): List[MarketDataEntry] =
    val requests = priceMap.keys.toList.sorted
      .sliding(2)
      .flatMap {
        case Seq(previous, current) =>
          Some(previous, current)
        case _ => None
      }
      .flatMap { case (previous, current) =>
        val previousPrices = priceMap(previous).sortBy(_.snapshotTime)
        val currentPrices = priceMap(current).sortBy(_.snapshotTime)
        previousPrices.lastOption
          .map(lastPrice => buildSignalRequests(lastPrice = lastPrice, currentPrices = currentPrices))
          .getOrElse(List.empty)
      }
      .toList
    requests.map {
      case signals.OpenGapRequest(signalInputs) =>
        buildEntries(signalInputs = signalInputs, exchangeCloseTime = exchangeCloseTime)
      case _ => List.empty
    }
    List.empty

  def buildSignalRequests(lastPrice: StockPrice, currentPrices: List[StockPrice]): List[SignalFinderRequest] =
    currentPrices.zipWithIndex.map { case (currentPrice: StockPrice, index: Int) =>
      val prices = List(lastPrice) ++ currentPrices.take(index + 1)
      openGapMarketDataStrategyProvider.buildFromPrices(prices = prices).buildSignalFinderRequest()
    }

  private def buildEntries(
      signalInputs: List[OpenGapSignalInput],
      exchangeCloseTime: LocalTime
  ): List[MarketDataEntry] =
    val lastSignalInputs = signalInputs.last
    val lastTradingPrice = lastSignalInputs.currentPrices.last.close
    val lastTradingTime = lastSignalInputs.tradingDateTime
    signalInputs.flatMap { input =>
      input.currentPrices.zipWithIndex.map { case (stockPrice: StockPrice, index: Int) =>
        val currentPrices = input.currentPrices.take(index + 1)
        val signalInput = input.copy(currentPrices = currentPrices)
        MarketDataEntry(
          tradingPrice = stockPrice.close,
          tradingTime = input.tradingDateTime,
          marketDataStrategyResponse = Success(OpenGapMarketDataStrategyResponse(signalInputs = List(signalInput)))
        )
      } ++ List(
        MarketDataEntry(
          tradingPrice = lastTradingPrice,
          tradingTime = LocalDateTime.of(lastTradingTime.toLocalDate, exchangeCloseTime),
          marketDataStrategyResponse = Success(OpenGapMarketDataStrategyResponse(signalInputs = List.empty))
        )
      )
    }
