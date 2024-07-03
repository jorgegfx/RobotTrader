package com.jworkdev.trading.robot.data.signals

object SignalFinderStrategy {
  def findSignals(signalFinderRequest: SignalFinderRequest): List[Signal] = {
    signalFinderRequest match
      case request: MovingAverageRequest =>
        MovingAverageSignalFinder().find(request = request)
      case request: RelativeStrengthIndexRequest =>
        RelativeStrengthIndexSignalFinder().find(request = request)
      case request: MACDRequest =>
        MACDValidatedSignalFinder().find(request = request)
      case request: OpenGapRequest =>
        OpenGapSignalFinder().find(request = request)
      case _ => List.empty
  }
}
