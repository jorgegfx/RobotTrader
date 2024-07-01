package com.jworkdev.trading.robot.data.signals

import com.jworkdev.trading.robot.data.signals.validator.IndicatorValidator
import com.jworkdev.trading.robot.data.signals.validator.IndicatorValidatorType.{RSI, Volume}

class MACDValidatedSignalFinder extends SignalFinder[MACDRequest]:
  def find(request: MACDRequest): List[Signal] = {
    val signals = MovingAverageConvergenceDivergenceSignalFinder().find(request = request)
    if(request.validate){
      val volumeValidations = IndicatorValidator.validate(stockPrices = request.stockPrices,Volume)
      val rsiValidations = IndicatorValidator.validate(stockPrices = request.stockPrices,RSI)
      signals.filter(signal=>
        if(signal.`type`==SignalType.Buy)
          volumeValidations(signal.date).buyValidation && rsiValidations(signal.date).buyValidation
        else
          volumeValidations(signal.date).sellValidation && rsiValidations(signal.date).sellValidation
      )
    }else signals
  }
