package com.jworkdev.trading.robot.data.signals
//TODO find better name
trait SignalFinderStrategy:
  def findSignals(signalFinderRequest: SignalFinderRequest): List[Signal]

class SignalFinderStrategyImpl extends SignalFinderStrategy:
  override def findSignals(signalFinderRequest: SignalFinderRequest): List[Signal] = signalFinderRequest match
    case request: MovingAverageRequest =>
      MovingAverageSignalFinder().find(request = request)
    case request: RelativeStrengthIndexRequest =>
      RelativeStrengthIndexSignalFinder().find(request = request)
    case request: MACDRequest =>
      MACDValidatedSignalFinder().find(request = request)
    case request: OpenGapRequest =>
      OpenGapSignalFinder().find(request = request)

object SignalFinderStrategy:
  def apply(): SignalFinderStrategy = new SignalFinderStrategyImpl()
