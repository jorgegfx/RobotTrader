package com.jworkdev.trading.robot.data.signals

import com.jworkdev.trading.robot.data.strategy.opengap.OpenGapSignalInput
import com.jworkdev.trading.robot.market.data.StockPrice

class OpenGapSignalFinder extends SignalFinder[OpenGapRequest]:
  private val limitFilledPercentage = 80
  private val minPercentageChange = 2
  private val maxPercentageChange = 8
  private val minProfit = 4

  override def find(request: OpenGapRequest): List[Signal] =
    request.signalInputs.filter(isValid).flatMap(findSignals)

  private def isValid(input: OpenGapSignalInput): Boolean =
    isInRange(before = input.closingPrice,after = input.openingPrice)



  private def isInRange(before: Double,
                        after: Double): Boolean = {
    val percentage = getPercentageChange(before = before, after = after)
    percentage > minPercentageChange && percentage < maxPercentageChange
  }

  private def getPercentageChange(before: Double,
                                  after: Double): Double = Math.abs(before - after)/before * 100

  private def isFilled(openingGap: Double, currentPrices: List[StockPrice]): Boolean =
    (for
      entry <- currentPrices.headOption
      exit <- currentPrices.lastOption
    yield Math.abs(entry.close - exit.close)).exists(currentGap => {
      val percentage = currentGap/openingGap * 100
      percentage > limitFilledPercentage
    })


  private def findSignals(input: OpenGapSignalInput): List[Signal]=
    val openingGap = Math.abs(input.closingPrice - input.openingPrice)
    if(isFilled(openingGap = openingGap, currentPrices = input.currentPrices))
      List.empty
    else
      val entrySignalType = if (input.closingPrice > input.openingPrice)
        SignalType.Buy
      else SignalType.Sell
      val entrySignal = input.currentPrices.headOption.map(entryPrice => {
        Signal(date = entryPrice.snapshotTime, `type` = entrySignalType, stockPrice = entryPrice)
      })
      val exitSignal = for
        entryPrice <- entrySignal.map(_.stockPrice.close)
        exitSignalType <- if (entrySignalType == SignalType.Buy) Some(SignalType.Sell) else None
        exitSignal <- createExitSignal(closingPrice = input.closingPrice,
          entryPrice = entryPrice,
          currentPrices = input.currentPrices)
      yield exitSignal
      List(entrySignal, exitSignal).flatten

  private def createExitSignal(closingPrice: Double,
                               entryPrice: Double,
                               currentPrices: List[StockPrice]): Option[Signal] =
    currentPrices.lastOption.filter(exitPrice => {
      val percentage = getPercentageChange(before = entryPrice,after = exitPrice.close)
      entryPrice < exitPrice.close && percentage >= minProfit
    }).map(exitPrice => {
      Signal(date = exitPrice.snapshotTime, `type` = SignalType.Sell, stockPrice = exitPrice)
    })