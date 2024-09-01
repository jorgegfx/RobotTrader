package com.jworkdev.trading.robot.strategy
import com.jworkdev.trading.robot
import com.jworkdev.trading.robot.OrderType.Sell
import com.jworkdev.trading.robot.data.strategy.{MarketDataStrategyResponse, opengap}
import com.jworkdev.trading.robot.service.OrderFactory
import com.jworkdev.trading.robot.{Order, OrderTrigger}
import com.typesafe.scalalogging.Logger

import scala.util.{Failure, Success}

class OpenGapTradingStrategyExecutor(orderFactory: OrderFactory) extends TradingStrategyExecutor:

  private val logger = Logger(classOf[OpenGapTradingStrategyExecutor])
  private val profitPercentageExit = 60

  override def executeEntry(request: TradingStrategyEntryRequest): Option[Order] =
    logger.info(s"Executing Entry on OpenGap with request: $request")
    orderFactory.createBuy(
      finInstrument = request.finInstrument,
      tradeDateTime = request.tradeDateTime,
      tradingMode = request.tradingMode,
      tradingExchange = request.exchange,
      balancePerFinInst = request.balancePerFinInst,
      tradingPrice = request.tradingPrice,
      tradingStrategy = request.tradingStrategy,
      marketDataStrategyResponse = request.marketDataStrategyResponse
    )

  private def isReadyToClose(openPricePerShare: Double, currentTradingPrice: Double): Boolean =
    if currentTradingPrice > openPricePerShare then
      val profit = currentTradingPrice - openPricePerShare
      val profitPercentage = profit / openPricePerShare
      profitPercentageExit >= profitPercentage
    else false

  override def executeExit(request: TradingStrategyExitRequest): Option[Order] =
    logger.info(s"Executing Exit on OpenGap with request: $request")
    orderFactory.createSell(
      position = request.position,
      finInstrument = request.finInstrument,
      tradingExchange = request.exchange,
      tradingMode = request.tradingMode,
      stopLossPercentage = request.stopLossPercentage,
      tradingPrice = request.tradingPrice,
      tradeDateTime = request.tradeDateTime,
      marketDataStrategyResponse = request.marketDataStrategyResponse
    ) match
      case Some(order) => Some(order)
      case None =>
        if isReadyToClose(
          openPricePerShare = request.position.openPricePerShare,
          currentTradingPrice = request.tradingPrice
        )
        then
          val order = Order(
            `type` = Sell,
            symbol = request.position.symbol,
            dateTime = request.tradeDateTime,
            shares = request.position.numberOfShares,
            price = request.tradingPrice,
            tradingStrategyType = request.position.tradingStrategyType,
            trigger = OrderTrigger.MaxProfitExit
          )
          logger.info(s"Creating Sell Order: $order PnL: ${order.totalPrice - request.position.totalOpenPrice}")
          Some(order)
        else
          logger.info(s"${request.position.id} has not reached limit " +
            s"openPricePerShare:${request.position.openPricePerShare} " +
            s"currentTradingPrice:${request.tradingPrice}")
          None

