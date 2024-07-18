package com.jworkdev.trading.robot.market.data

import org.scalatest.flatspec.AnyFlatSpec

class VolatilityCalculatorTest extends AnyFlatSpec:
  it should "have no volatility when data does not have changes" in {
    val volatility = VolatilityCalculator.calculate(List(1.0D,1.0D,1.0D))
    assert(volatility === 0.0)
  }
  it should "have volatility when data have changes" in {
    val volatility = VolatilityCalculator.calculate(List(1.0D, 2.0D, 1.0D))
    println(volatility)
  }
