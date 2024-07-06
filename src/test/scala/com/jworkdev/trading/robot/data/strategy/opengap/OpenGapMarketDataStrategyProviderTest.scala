package com.jworkdev.trading.robot.data.strategy.opengap

import com.jworkdev.trading.robot.market.data.MarketDataProvider
import org.scalatest.funsuite.AnyFunSuiteLike
import org.scalatestplus.mockito.MockitoSugar.mock

import scala.util.{Failure, Success}

class OpenGapMarketDataStrategyProviderTest extends AnyFunSuiteLike:
  private val marketDataProvider: MarketDataProvider = mock[MarketDataProvider]
  test("testProvide") {
    val provider = OpenGapMarketDataStrategyProvider(marketDataProvider)
    provider.provide(request = OpenGapMarketDataStrategyRequest(symbol = "NVDA", signalCount = 2)) match
      case Failure(exception) => fail(exception)
      case Success(value) =>
        assert(value.signalInputs.nonEmpty)
        assert(2 === value.signalInputs.size)

  }
