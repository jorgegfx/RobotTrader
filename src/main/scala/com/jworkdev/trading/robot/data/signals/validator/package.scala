package com.jworkdev.trading.robot.data.signals

import com.jworkdev.trading.robot.market
import com.jworkdev.trading.robot.market.data.StockPrice

import java.time.{Instant, ZonedDateTime}

package object validator {

  case class ValidationResult( sellValidation: Boolean, buyValidation: Boolean)
  enum IndicatorValidatorType:
    case Volume, RSI

  trait IndicatorValidator:
    def validate(stockPrices: List[StockPrice]): Map[ZonedDateTime,ValidationResult]

  object IndicatorValidator{
    private val validatorMap = Map[IndicatorValidatorType, IndicatorValidator](
      IndicatorValidatorType.Volume -> VolumeIndicatorValidator(),
      IndicatorValidatorType.RSI -> RSIIndicatorValidator(period = 14))

    def validate(stockPrices: List[StockPrice],
                 indicatorValidatorType: IndicatorValidatorType): Map[ZonedDateTime,ValidationResult] = 
      validatorMap(indicatorValidatorType).validate(stockPrices = stockPrices)
  }
}
