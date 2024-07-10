package com.jworkdev.trading.robot.service

import com.jworkdev.trading.robot.domain.FinInstrument
import com.jworkdev.trading.robot.domain.FinInstrumentType.Stock
import com.jworkdev.trading.robot.market.data.SnapshotInterval.SixtyMinutes
import com.jworkdev.trading.robot.market.data.{ExchangeDataProvider, MarketDataProvider, VolatilityCalculator}
import zio.{Task, ZIO, ZLayer}

import java.time.Instant

trait StockScreeningService:
  def screenFinInstruments(): Task[List[FinInstrument]]

class StockScreeningServiceImpl(
    private val exchangeDataProvider: ExchangeDataProvider,
    private val marketDataProvider: MarketDataProvider
) extends StockScreeningService:
  override def screenFinInstruments(): Task[List[FinInstrument]] = for
    symbols <- exchangeDataProvider.findAllSymbols("NASDAQ", Stock)
    fibers <- ZIO.foreach(symbols) { symbol =>
      buildFinInstrument(symbol = symbol).fork
    }
    results <- ZIO.foreach(fibers)(_.join)
  yield results

  private def buildFinInstrument(symbol: String): Task[FinInstrument] =
    calculateVolatility(symbol = symbol).map(volatility =>
      FinInstrument(
        symbol = symbol,
        `type` = Stock,
        volatility = volatility,
        exchange = "NASDAQ",
        creationDate = Instant.now()
      )
    )

  private def calculateVolatility(symbol: String): Task[Double] =
    ZIO.attemptBlocking(
      marketDataProvider
        .getIntradayQuotesDaysRange(symbol = symbol, interval = SixtyMinutes, daysRange = 7)
        .fold(ex => 0d, VolatilityCalculator.calculate)
    )

object StockScreeningService:
  val layer: ZLayer[ExchangeDataProvider, Nothing, StockScreeningService] =
    ZLayer {
      for exchangeDataProvider <- ZIO.service[ExchangeDataProvider]
      yield StockScreeningServiceImpl(exchangeDataProvider, MarketDataProvider())
    }
