package com.jworkdev.trading.robot.data.strategy.opengap

import org.scalatest.funsuite.AnyFunSuiteLike

import scala.util.{Failure, Success}

class OpenGapMarketDataStrategyProviderTest extends AnyFunSuiteLike:

  test("testProvide") {
    val provider = OpenGapMarketDataStrategyProvider()
    provider.provide(request = OpenGapMarketDataStrategyRequest(symbol = "NVDA", signalCount = 2)) match
      case Failure(exception) => fail(exception)
      case Success(value) =>
        assert(value.signalInputs.nonEmpty)
        assert(2 === value.signalInputs.size)

  }
