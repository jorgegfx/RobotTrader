package com.jworkdev.trading.robot.strategy
import com.jworkdev.trading.robot
import com.jworkdev.trading.robot.OrderType.Sell
import com.jworkdev.trading.robot.data.strategy.{MarketDataStrategyResponse, opengap}
import com.jworkdev.trading.robot.service.OrderFactory
import com.jworkdev.trading.robot.{Order, OrderTrigger}
import com.typesafe.scalalogging.Logger

import scala.util.{Failure, Success}

class OpenGapTradingStrategyExecutor(orderFactory: OrderFactory)
    extends TradingStrategyExecutor:

  private val logger = Logger(classOf[OpenGapTradingStrategyExecutor])
  private val profitExit = 60

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

  /**
   * TODO : More testing
   */
  private def isGapReadyToClose(marketDataStrategyResponse: MarketDataStrategyResponse,
                                currentTradingPrice: Double): Boolean =
    marketDataStrategyResponse match
      case opengap.OpenGapMarketDataStrategyResponse(signalInputs) =>
        signalInputs.headOption.filter(signal=> currentTradingPrice > signal.closingPrice).exists(signal=>{
          val gap = signal.closingPrice - signal.openingPrice
          val currentGap = currentTradingPrice - signal.closingPrice
          val percentageFilled = currentGap/gap * 100
          percentageFilled > profitExit
        })
      case _ => false

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
        request.marketDataStrategyResponse match
          case Failure(exception) =>
            logger.error("Error fetching market data:",exception)
            None
          case Success(response) =>
            val profit = request.tradingPrice - request.position.openPricePerShare
            if (profit > 0 && isGapReadyToClose(marketDataStrategyResponse = response,
                                                currentTradingPrice = request.tradingPrice)) {
              val order = Order(
                `type` = Sell,
                symbol = request.position.symbol,
                dateTime = request.tradeDateTime,
                shares = request.position.numberOfShares,
                price = request.tradingPrice,
                tradingStrategyType = request.position.tradingStrategyType,
                trigger = OrderTrigger.Signal
              )
              logger.info(s"Creating Sell Order: $order PnL: ${order.totalPrice - request.position.totalOpenPrice}")
              Some(order)
            }else None
