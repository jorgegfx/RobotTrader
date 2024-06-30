package com.jworkdev.trading.robot.data.signals

import com.jworkdev.trading.robot.data.StockPrice

import java.time.Instant

package object validator {

  trait IndicatorValidator:
    def validate(stockPrices: List[StockPrice]): Map[Instant,Boolean]
}
