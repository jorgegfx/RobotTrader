package com.jworkdev.trading.robot.data.signals.validator

import com.jworkdev.trading.robot.market.data

import java.time.Instant

class VolumeIndicatorValidator extends IndicatorValidator{
  override def validate(stockPrices: List[data.StockPrice]): Map[Instant, ValidationResult] = {
    if(stockPrices.isEmpty) return Map.empty
    val avgVolume = stockPrices.map(_.volume).sum / stockPrices.size
    stockPrices.map(price=>
      val volumePassed = price.volume>= avgVolume * 1.5
      (price.snapshotTime,ValidationResult(volumePassed, volumePassed))
    ).toMap
  }
}
