package com.jworkdev.trading.robot.strategy

import com.jworkdev.trading.robot.Order
import com.jworkdev.trading.robot.service.OrderFactory
import com.typesafe.scalalogging.Logger

class MACDTradingStrategyExecutor(orderFactory: OrderFactory)
    extends TradingStrategyExecutor:
  private val logger = Logger(classOf[MACDTradingStrategyExecutor])

  override def executeEntry(request: TradingStrategyEntryRequest): Option[Order] =
    logger.info(s"Executing Entry on MACD with request: $request")
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

  override def executeExit(request: TradingStrategyExitRequest): Option[Order] =
    logger.info(s"Executing Exit on MACD with request: $request")
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
      case None => None

