package com.jworkdev.trading.robot.data.signals.validator

import com.jworkdev.trading.robot.data

import java.time.Instant

class VolumeIndicatorValidator extends IndicatorValidator{
  override def validate(stockPrices: List[data.StockPrice]): Map[Instant, Boolean] = {
    if(stockPrices.isEmpty) return Map.empty
    val avgVolume = stockPrices.map(_.volume).sum / stockPrices.size
    stockPrices.map(price=>
      (price.snapshotTime,price.volume>= avgVolume * 1.5)
    ).toMap
  }
}
