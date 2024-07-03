package com.jworkdev.trading.robot.data.signals

class OpenGapSignalFinder extends SignalFinder[OpenGapRequest]:
  override def find(request: OpenGapRequest): List[Signal] =
    request.signalInputs.map(input=>{
      val signalType = if(input.closingPrice > input.openingPrice)
        SignalType.Buy
      else SignalType.Sell
      Signal(date=input.currentPrice.snapshotTime,`type`=signalType,stockPrice = input.currentPrice)
    })