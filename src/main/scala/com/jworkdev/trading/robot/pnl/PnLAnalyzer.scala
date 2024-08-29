package com.jworkdev.trading.robot.pnl

import com.jworkdev.trading.robot.data.signals.{Signal, SignalFinderStrategy}
import com.jworkdev.trading.robot.data.signals.SignalType.{Buy, Sell}
import com.jworkdev.trading.robot.domain.TradingStrategyType
import com.jworkdev.trading.robot.service.{ForcePositionExitService, OrderFactory}
import com.jworkdev.trading.robot.{Order, OrderTrigger, OrderType}
import com.typesafe.scalalogging.Logger

import scala.collection.mutable

case class PnLAnalysis(pnl: Double, orders: List[Order])

trait PnLAnalyzer:
  def execute(initialCash: Double, signals: List[Signal], tradingStrategyType: TradingStrategyType): PnLAnalysis

class PnLAnalyzerImpl(signalFinderStrategy: SignalFinderStrategy) extends PnLAnalyzer:
  private val logger = Logger(classOf[PnLAnalyzerImpl])
 
  override def execute(
      initialCash: Double,
      signals: List[Signal],
      tradingStrategyType: TradingStrategyType
  ): PnLAnalysis =
    logger.info(s"Starting execution initialCash:$initialCash")
    var cash = initialCash // Initial cash
    var position = 0 // Number of shares held
    var currentPosition = (0,0.0D)
    var totalGain = 0.0D;
    val orders = mutable.ListBuffer.empty[Order]

    signals.foreach { signal =>
      val orderPrice = signal.stockPrice.close
      signal.`type` match
        case Buy if cash >= orderPrice =>
          val sharesToBuy = (cash / orderPrice).toInt
          cash -= sharesToBuy * orderPrice
          currentPosition = (currentPosition._1+sharesToBuy,orderPrice)
          orders += Order(
            `type` = OrderType.Buy,
            symbol = signal.stockPrice.symbol,
            dateTime = signal.date,
            shares = sharesToBuy,
            price = orderPrice,
            tradingStrategyType = tradingStrategyType,
            trigger = OrderTrigger.Signal
          )

        case Sell if currentPosition._1 > 0 =>
          cash += currentPosition._1 * orderPrice
          totalGain += currentPosition._1*(orderPrice-currentPosition._2)
          orders += Order(
            `type` = OrderType.Sell,
            symbol = signal.stockPrice.symbol,
            dateTime = signal.date,
            shares = position,
            price = orderPrice,
            tradingStrategyType = tradingStrategyType,
            trigger = OrderTrigger.Signal
          )
          currentPosition = (0,0.0D)
        case _ =>
    }
    val pnl = totalGain
    logger.info(s"Final Cash: $$${cash.formatted("%.2f")}")
    logger.info(s"Final Position: $position shares")
    logger.info(s"Final PnL: $$${pnl.formatted("%.2f")}")
    PnLAnalysis(pnl = pnl, orders = orders.toList)

object PnLAnalyzer:
  def apply(): PnLAnalyzer = new PnLAnalyzerImpl(signalFinderStrategy = SignalFinderStrategy())