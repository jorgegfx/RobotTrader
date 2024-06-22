package com.jworkdev.trading.robot.pnl

import com.jworkdev.trading.robot.{Order, OrderType}
import com.jworkdev.trading.robot.data.StockPrice
import com.jworkdev.trading.robot.data.signals.Signal
import com.jworkdev.trading.robot.data.signals.SignalType.{Buy, Sell}
import com.typesafe.scalalogging.Logger

import scala.collection.mutable

case class PnLAnalysis(pnl: Double, orders: List[Order])

trait PnLAnalyzer:
  def execute(initialCash: Double,prices: List[StockPrice], signals: List[Signal]): PnLAnalysis

class PnLAnalyzerImpl extends PnLAnalyzer:
  private val logger = Logger(classOf[PnLAnalyzerImpl])

  override def execute(
      initialCash: Double,
      prices: List[StockPrice],
      signals: List[Signal]
  ): PnLAnalysis =
    logger.info(s"Starting execution initialCash:$initialCash")
    var cash = initialCash // Initial cash
    var position = 0 // Number of shares held
    var pnl = 0.0 // Profit and Loss
    val orders = mutable.ListBuffer.empty[Order]

    signals.foreach { signal =>
      prices.find(_.snapshotTime == signal.date) match
        case Some(stockPrice) =>
          val orderPrice = stockPrice.close
          signal.`type` match
            case Buy if cash >= orderPrice =>
              val sharesToBuy = (cash / orderPrice).toInt
              cash -= sharesToBuy * orderPrice
              position += sharesToBuy
              orders += Order(
                `type` = OrderType.Buy,
                symbol = stockPrice.symbol,
                dateTime = signal.date,
                shares = sharesToBuy,
                price = orderPrice
              )

            case Sell if position > 0 =>
              cash += position * orderPrice
              pnl += (position * orderPrice) - (orders.collect {
                case order: Order => order.shares * order.price
              }.sum / orders.count(_._1 == OrderType.Buy))
              orders += Order(
                `type` = OrderType.Sell,
                symbol = stockPrice.symbol,
                dateTime = signal.date,
                shares = position,
                price = orderPrice
              )
              position = 0
            case _ =>
        case None =>
    }
    logger.info(s"Final Cash: $$${cash.formatted("%.2f")}")
    logger.info(s"Final Position: $position shares")
    logger.info(s"Final PnL: $$${pnl.formatted("%.2f")}")
    PnLAnalysis(pnl = pnl, orders = orders.toList)

object PnLAnalyzer:
  def apply(): PnLAnalyzer = new PnLAnalyzerImpl()