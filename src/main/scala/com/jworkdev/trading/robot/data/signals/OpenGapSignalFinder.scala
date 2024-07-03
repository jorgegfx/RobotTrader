package com.jworkdev.trading.robot.data.signals

import com.jworkdev.trading.robot.data.strategy.opengap.OpenGapSignalInput

class OpenGapSignalFinder extends SignalFinder[OpenGapRequest]:

  private def getPercentageChange(before: Double,
                                  after: Double): Double = Math.abs(before - after)/before * 100

  private def isInRange(before: Double,
                        after: Double): Boolean = {
    val percentage = getPercentageChange(before = before, after = after)
    percentage > 2 && percentage < 8
  }

  private def isValid(input: OpenGapSignalInput): Boolean =
    isInRange(before = input.closingPrice,after = input.openingPrice)

  override def find(request: OpenGapRequest): List[Signal] =
    request.signalInputs.filter(isValid).flatMap(input=>{
      val gap = Math.abs(input.closingPrice - input.openingPrice)
      val entrySignalType = if(input.closingPrice > input.openingPrice)
        SignalType.Buy
      else SignalType.Sell
      val entrySignal = input.currentPrices.headOption.map(entryPrice=>{
        Signal(date=entryPrice.snapshotTime,`type`=entrySignalType,stockPrice = entryPrice)
      })
      val exitSignalType = if(entrySignalType == SignalType.Buy) SignalType.Sell else SignalType.Buy
      val exitSignal = input.currentPrices.findLast(exitPrice=>{
        val currentGap = Math.abs(input.closingPrice - exitPrice.close)
        val percentage = getPercentageChange(before = gap, after = currentGap)
        percentage < 80
      }).map(exitPrice=>{
        Signal(date=exitPrice.snapshotTime,`type`=exitSignalType,stockPrice = exitPrice)
      })
      List(entrySignal,exitSignal).flatten
    })