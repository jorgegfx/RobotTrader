package com.jworkdev.trading.robot.service

import com.jworkdev.trading.robot.Order
import com.jworkdev.trading.robot.OrderType.{Buy, Sell}
import com.jworkdev.trading.robot.config.TradingMode
import com.jworkdev.trading.robot.data.signals.{Signal, SignalFinderStrategy, SignalType}
import com.jworkdev.trading.robot.data.strategy.MarketDataStrategyResponse
import com.jworkdev.trading.robot.domain.{FinInstrument, Position, TradingExchange, TradingStrategy}
import com.jworkdev.trading.robot.time.ZonedDateTimeExtensions.isSameDay
import com.jworkdev.trading.robot.time.LocalDateTimeExtensions.toZonedDateTime
import com.typesafe.scalalogging.Logger

import java.time.{Instant, LocalDateTime, ZonedDateTime}
import scala.util.{Failure, Success, Try}
case class OrderRequest(
    balancePerFinInst: Double,
    finInstrument: FinInstrument,
    tradingStrategy: TradingStrategy,
    openPosition: Option[Position],
    exchangeMap: Map[String, TradingExchange],
    tradingMode: TradingMode,
    stopLossPercentage: Int,
    tradingPrice: Double,
    tradeDateTime: LocalDateTime,
    marketDataStrategyResponse: Try[MarketDataStrategyResponse]
)

trait OrderFactory:
  def create(orderRequest: OrderRequest): Option[Order]

class OrderFactoryImpl(signalFinderStrategy: SignalFinderStrategy) extends OrderFactory:
  private val logger = Logger(classOf[OrderFactoryImpl])

  def create(orderRequest: OrderRequest): Option[Order] =
    logger.info(s"Trading on  ${orderRequest.finInstrument.symbol} tradeDateTime:${orderRequest.tradeDateTime} ...")
    orderRequest.marketDataStrategyResponse match
      case Failure(exception) =>
        logger.error("Error getting strategy market data!", exception)
        orderRequest.openPosition.flatMap(position =>
          executeCloseOrStopLoss(
            finInstrument = orderRequest.finInstrument,
            position = position,
            currentPrice = orderRequest.tradingPrice,
            stopLossPercentage = orderRequest.stopLossPercentage,
            tradeDateTime = orderRequest.tradeDateTime,
            tradingStrategy = orderRequest.tradingStrategy,
            exchangeMap = orderRequest.exchangeMap,
            tradingMode = orderRequest.tradingMode
          )
        )
      case Success(marketDataResponse) =>
        val signals =
          signalFinderStrategy.findSignals(signalFinderRequest = marketDataResponse.buildSignalFinderRequest())
        create(orderRequest = orderRequest, lastSignal = signals.lastOption)

  private def create(orderRequest: OrderRequest, lastSignal: Option[Signal]): Option[Order] = lastSignal match
    case Some(signal) =>
      logger.info(s"Last Signal: $signal")
      orderRequest.openPosition match
        case Some(position) =>
          // Trying to make a Sell
          executeSellSignal(
            signal = signal,
            finInstrument = orderRequest.finInstrument,
            tradingStrategy = orderRequest.tradingStrategy,
            position = position,
            stopLossPercentage = orderRequest.stopLossPercentage,
            currentPrice = orderRequest.tradingPrice,
            tradeDateTime = orderRequest.tradeDateTime,
            exchangeMap = orderRequest.exchangeMap,
            tradingMode = orderRequest.tradingMode
          )
        case None =>
          // Trying to make a Buy
          executeBuySignal(
            signal = signal,
            finInstrument = orderRequest.finInstrument,
            tradingMode = orderRequest.tradingMode,
            tradingStrategy = orderRequest.tradingStrategy,
            balancePerFinInst = orderRequest.balancePerFinInst,
            currentPrice = orderRequest.tradingPrice,
            exchangeMap = orderRequest.exchangeMap,
            tradingDateTime = orderRequest.tradeDateTime
          )
    case None =>
      logger.info(s"No Last Signal found!")
      orderRequest.openPosition.flatMap(position =>
        executeCloseOrStopLoss(
          finInstrument = orderRequest.finInstrument,
          position = position,
          currentPrice = orderRequest.tradingPrice,
          stopLossPercentage = orderRequest.stopLossPercentage,
          tradeDateTime = orderRequest.tradeDateTime,
          tradingStrategy = orderRequest.tradingStrategy,
          exchangeMap = orderRequest.exchangeMap,
          tradingMode = orderRequest.tradingMode
        )
      )

  private def executeSellSignal(
      signal: Signal,
      finInstrument: FinInstrument,
      tradingStrategy: TradingStrategy,
      position: Position,
      stopLossPercentage: Int,
      currentPrice: Double,
      tradeDateTime: LocalDateTime,
      exchangeMap: Map[String, TradingExchange],
      tradingMode: TradingMode
  ): Option[Order] =
    if signal.`type` == SignalType.Sell then
      val order =
        Order(
          `type` = Sell,
          symbol = finInstrument.symbol,
          dateTime = ZonedDateTime.now(),
          shares = position.numberOfShares,
          price = currentPrice,
          positionId = Some(position.id),
          tradingStrategyType = tradingStrategy.`type`
        )
      logger.info(s"Creating Sell Order: $order")
      Some(order)
    else
      logger.info(s"No Sell Signal")
      executeCloseOrStopLoss(
        finInstrument = finInstrument,
        position = position,
        currentPrice = currentPrice,
        stopLossPercentage = stopLossPercentage,
        tradeDateTime = tradeDateTime,
        tradingStrategy = tradingStrategy,
        exchangeMap = exchangeMap,
        tradingMode = tradingMode
      )

  private def executeCloseOrStopLoss(
      finInstrument: FinInstrument,
      position: Position,
      currentPrice: Double,
      stopLossPercentage: Int,
      tradeDateTime: LocalDateTime,
      tradingStrategy: TradingStrategy,
      exchangeMap: Map[String, TradingExchange],
      tradingMode: TradingMode
  ): Option[Order] =
    executeStopLoss(
      finInstrument = finInstrument,
      position = position,
      currentPrice = currentPrice,
      stopLossPercentage = stopLossPercentage,
      tradingStrategy = tradingStrategy
    ) match
      case Some(value) => Some(value)
      case None =>
        executeCloseDay(
          finInstrument = finInstrument,
          position = position,
          currentPrice = currentPrice,
          tradeDateTime = tradeDateTime,
          tradingStrategy = tradingStrategy,
          exchangeMap = exchangeMap,
          tradingMode = tradingMode
        )

  private def executeCloseDay(
      finInstrument: FinInstrument,
      position: Position,
      currentPrice: Double,
      tradeDateTime: LocalDateTime,
      tradingStrategy: TradingStrategy,
      exchangeMap: Map[String, TradingExchange],
      tradingMode: TradingMode
  ): Option[Order] =
    if TradingWindowValidator.shouldCloseDay(
        tradingDateTime = tradeDateTime,
        tradingMode = tradingMode,
        finInstrument = finInstrument,
        tradingExchangeMap = exchangeMap
      )
    then
      logger.info(s"Close Day for Position : ${position.id}!")
      Some(
        Order(
          `type` = Sell,
          symbol = finInstrument.symbol,
          dateTime = ZonedDateTime.now(),
          shares = position.numberOfShares,
          price = currentPrice,
          positionId = Some(position.id),
          tradingStrategyType = tradingStrategy.`type`
        )
      )
    else None

  private def executeStopLoss(
      finInstrument: FinInstrument,
      position: Position,
      currentPrice: Double,
      stopLossPercentage: Int,
      tradingStrategy: TradingStrategy
  ): Option[Order] =
    if position.shouldExitForStopLoss(
        currentPricePerShare = currentPrice,
        stopLossPercentage = stopLossPercentage
      )
    then
      logger.info(s"Stop Loss for Position : ${position.id}!")
      Some(
        Order(
          `type` = Sell,
          symbol = finInstrument.symbol,
          dateTime = ZonedDateTime.now(),
          shares = position.numberOfShares,
          price = currentPrice,
          positionId = Some(position.id),
          tradingStrategyType = tradingStrategy.`type`
        )
      )
    else None

  private def executeBuySignal(
      signal: Signal,
      finInstrument: FinInstrument,
      tradingStrategy: TradingStrategy,
      exchangeMap: Map[String, TradingExchange],
      tradingMode: TradingMode,
      balancePerFinInst: Double,
      currentPrice: Double,
      tradingDateTime: LocalDateTime
  ): Option[Order] =
    if signal.`type` == SignalType.Buy then
      if signal.date.isSameDay(localDateTime = tradingDateTime) &&
        TradingWindowValidator.isNotOutOfBuyingWindow(
          tradingDateTime = tradingDateTime,
          tradingMode = tradingMode,
          finInstrument = finInstrument,
          tradingExchangeMap = exchangeMap
        )
      then
        val numberOfShares = (balancePerFinInst / currentPrice).toLong
        if numberOfShares > 0 then
          val order =
            Order(
              `type` = Buy,
              symbol = finInstrument.symbol,
              dateTime = tradingDateTime.toZonedDateTime,
              shares = numberOfShares,
              price = currentPrice,
              tradingStrategyType = tradingStrategy.`type`
            )
          logger.info(s"Creating Buy Order: $order")
          Some(order)
        else
          logger.info("Not enough of cash to buy!")
          None
      else
        logger.info(
          s"Is closing IntraDay signal = $signal, " +
            s"tradingMode = $tradingMode, " +
            s"finInstrument = $finInstrument, tradingExchangeMap = $exchangeMap"
        )
        None
    else
      logger.info(s"No Buy Signal")
      None

object OrderFactory:
  def apply(signalFinderStrategy: SignalFinderStrategy): OrderFactory =
    new OrderFactoryImpl(signalFinderStrategy = signalFinderStrategy)
