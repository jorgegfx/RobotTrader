package com.jworkdev.trading.robot.service

import com.jworkdev.trading.robot.OrderType.{Buy, Sell}
import com.jworkdev.trading.robot.{Order, OrderTrigger}
import com.jworkdev.trading.robot.config.TradingMode
import com.jworkdev.trading.robot.data.signals.{Signal, SignalFinderRequest, SignalFinderStrategy, SignalType}
import com.jworkdev.trading.robot.data.strategy.MarketDataStrategyResponse
import com.jworkdev.trading.robot.domain.*
import com.typesafe.scalalogging.Logger
import com.jworkdev.trading.robot.time.ZonedDateTimeExtensions.isSameDay
import com.jworkdev.trading.robot.data.signals.SignalType.{Buy as SignalBuy, Sell as SignalSell}

import java.time.ZonedDateTime
import scala.util.{Failure, Success, Try}

trait OrderFactory:

  def createSell(
      symbol: String,
      dateTime: ZonedDateTime,
      shares: Long,
      price: Double,
      tradingStrategyType: TradingStrategyType,
      positionId: Long,
      trigger: OrderTrigger
  ): Order

  def createBuy(
      symbol: String,
      dateTime: ZonedDateTime,
      shares: Long,
      price: Double,
      tradingStrategyType: TradingStrategyType,
      trigger: OrderTrigger
  ): Order

  def createSell(
      position: Position,
      finInstrument: FinInstrument,
      tradingExchange: TradingExchange,
      tradingMode: TradingMode,
      stopLossPercentage: Int,
      tradingPrice: Double,
      tradeDateTime: ZonedDateTime,
      marketDataStrategyResponse: Try[MarketDataStrategyResponse]
  ): Option[Order]

  def createBuy(
      finInstrument: FinInstrument,
      tradeDateTime: ZonedDateTime,
      tradingMode: TradingMode,
      tradingExchange: TradingExchange,
      balancePerFinInst: Double,
      tradingPrice: Double,
      tradingStrategy: TradingStrategy,
      marketDataStrategyResponse: Try[MarketDataStrategyResponse]
  ): Option[Order]

class OrderFactoryImpl(signalFinderStrategy: SignalFinderStrategy, forcePositionExitService: ForcePositionExitService)
    extends OrderFactory:
  private val logger = Logger(classOf[OrderFactoryImpl])

  override def createBuy(
      symbol: String,
      dateTime: ZonedDateTime,
      shares: Long,
      price: Double,
      tradingStrategyType: TradingStrategyType,
      trigger: OrderTrigger
  ): Order =
    logger.info(s"Creating Sell Order for Symbol: $symbol, trigger: $trigger, Price $price")
    Order(
      `type` = Buy,
      symbol = symbol,
      dateTime = dateTime,
      shares = shares,
      price = price,
      tradingStrategyType = tradingStrategyType,
      trigger = trigger
    )

  override def createSell(
      symbol: String,
      dateTime: ZonedDateTime,
      shares: Long,
      price: Double,
      tradingStrategyType: TradingStrategyType,
      positionId: Long,
      trigger: OrderTrigger
  ): Order =
    logger.info(s"Creating Sell Order for Symbol: $symbol, positionId: $positionId trigger: $trigger, Price $price")
    Order(
      `type` = Sell,
      symbol = symbol,
      dateTime = dateTime,
      shares = shares,
      price = price,
      tradingStrategyType = tradingStrategyType,
      positionId = Some(positionId),
      trigger = trigger
    )

  private def validate(
      signal: Signal,
      tradeDateTime: ZonedDateTime,
      tradingMode: TradingMode,
      finInstrument: FinInstrument,
      tradingExchange: TradingExchange
  ): Boolean =
    signal.date.isSameDay(other = tradeDateTime) &&
      TradingWindowValidator.isNotOutOfBuyingWindow(
        tradingDateTime = tradeDateTime,
        tradingMode = tradingMode,
        finInstrument = finInstrument,
        tradingExchange = tradingExchange
      )

  private def getCurrentDaySignals(
      signalFinderRequest: SignalFinderRequest,
      tradeDateTime: ZonedDateTime
  ): List[Signal] =
    signalFinderStrategy
      .findSignals(signalFinderRequest = signalFinderRequest)
      .filter(signal => signal.date.isSameDay(tradeDateTime))

  private def getLastSignal(
      signalType: SignalType,
      signalFinderRequest: SignalFinderRequest,
      tradeDateTime: ZonedDateTime
  ): Option[Signal] =
    val signals =
      getCurrentDaySignals(signalFinderRequest = signalFinderRequest, tradeDateTime = tradeDateTime)
    signals.lastOption.filter(signal => signal.`type` == signalType)

  override def createSell(
      position: Position,
      finInstrument: FinInstrument,
      tradingExchange: TradingExchange,
      tradingMode: TradingMode,
      stopLossPercentage: Int,
      tradingPrice: Double,
      tradeDateTime: ZonedDateTime,
      marketDataStrategyResponse: Try[MarketDataStrategyResponse]
  ): Option[Order] = marketDataStrategyResponse match
    case Failure(exception) =>
      logger.error("Error fetching market data!", exception)
      forcePositionExitService
        .executeCloseDayOrStopLoss(
          finInstrument = finInstrument,
          position = position,
          currentPrice = tradingPrice,
          stopLossPercentage = stopLossPercentage,
          tradeDateTime = tradeDateTime,
          tradingExchange = tradingExchange,
          tradingMode = tradingMode
        )
    case Success(response) =>
      getLastSignal(
        signalType = SignalSell,
        signalFinderRequest = response.buildSignalFinderRequest(),
        tradeDateTime = tradeDateTime
      ) match
        case Some(lastSignal) =>
          val order = createSell(
            symbol = position.symbol,
            dateTime = tradeDateTime,
            shares = position.numberOfShares,
            price = tradingPrice,
            tradingStrategyType = position.tradingStrategyType,
            trigger = OrderTrigger.Signal,
            positionId = position.id
          )
          logger.info(s"Creating Sell Order: $order PnL: ${order.totalPrice - position.totalOpenPrice}")
          Some(order)
        case None =>
          forcePositionExitService
            .executeCloseDayOrStopLoss(
              finInstrument = finInstrument,
              position = position,
              currentPrice = tradingPrice,
              stopLossPercentage = stopLossPercentage,
              tradeDateTime = tradeDateTime,
              tradingExchange = tradingExchange,
              tradingMode = tradingMode
            )

  private def createBuy(
      finInstrument: FinInstrument,
      tradeDateTime: ZonedDateTime,
      tradingMode: TradingMode,
      tradingExchange: TradingExchange,
      balancePerFinInst: Double,
      tradingPrice: Double,
      tradingStrategy: TradingStrategy,
      signalFinderRequest: SignalFinderRequest
  ): Option[Order] =
    getLastSignal(
      signalType = SignalBuy,
      signalFinderRequest = signalFinderRequest,
      tradeDateTime = tradeDateTime
    ) match
      case Some(lastSignal) =>
        if validate(
            signal = lastSignal,
            tradeDateTime = tradeDateTime,
            tradingMode = tradingMode,
            finInstrument = finInstrument,
            tradingExchange = tradingExchange
          )
        then
          val numberOfShares = (balancePerFinInst / tradingPrice).toLong
          if numberOfShares > 0 then
            val order = createBuy(
              symbol = finInstrument.symbol,
              dateTime = tradeDateTime,
              shares = numberOfShares,
              price = tradingPrice,
              tradingStrategyType = tradingStrategy.`type`,
              trigger = OrderTrigger.Signal
            )
            logger.info(s"Creating Buy Order: $order")
            Some(order)
          else
            logger.info("Not enough of cash to buy!")
            None
        else
          logger.info(
            s"Is closing IntraDay signal = $lastSignal, " +
              s"tradingMode = ${tradingMode}, " +
              s"finInstrument = ${finInstrument}, tradingExchange = ${tradingExchange}"
          )
          None
      case None => None

  override def createBuy(
      finInstrument: FinInstrument,
      tradeDateTime: ZonedDateTime,
      tradingMode: TradingMode,
      tradingExchange: TradingExchange,
      balancePerFinInst: Double,
      tradingPrice: Double,
      tradingStrategy: TradingStrategy,
      marketDataStrategyResponse: Try[MarketDataStrategyResponse]
  ): Option[Order] = marketDataStrategyResponse match
    case Failure(exception) => None
    case Success(response) =>
      createBuy(
        finInstrument = finInstrument,
        tradeDateTime = tradeDateTime,
        tradingMode = tradingMode,
        tradingExchange = tradingExchange,
        balancePerFinInst = balancePerFinInst,
        tradingPrice = tradingPrice,
        tradingStrategy = tradingStrategy,
        signalFinderRequest = response.buildSignalFinderRequest()
      )

object OrderFactory:
  def apply(
      signalFinderStrategy: SignalFinderStrategy,
      forcePositionExitService: ForcePositionExitService
  ): OrderFactory =
    new OrderFactoryImpl(
      signalFinderStrategy = signalFinderStrategy,
      forcePositionExitService = forcePositionExitService
    )
