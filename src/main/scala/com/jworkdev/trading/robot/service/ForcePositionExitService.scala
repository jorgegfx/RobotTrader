package com.jworkdev.trading.robot.service

import com.jworkdev.trading.robot.OrderType.Sell
import com.jworkdev.trading.robot.{Order, OrderTrigger}
import com.jworkdev.trading.robot.config.TradingMode
import com.jworkdev.trading.robot.domain.{FinInstrument, Position, TradingExchange, TradingStrategy, TradingStrategyType}
import com.typesafe.scalalogging.Logger

import java.time.ZonedDateTime

trait ForcePositionExitService :
  def executeCloseDayOrStopLoss(
                                      finInstrument: FinInstrument,
                                      position: Position,
                                      currentPrice: Double,
                                      stopLossPercentage: Int,
                                      tradeDateTime: ZonedDateTime,
                                      tradingExchange: TradingExchange,
                                      tradingMode: TradingMode
                                    ): Option[Order]


class ForcePositionExitServiceImpl extends ForcePositionExitService:
  private val logger = Logger(classOf[ForcePositionExitServiceImpl])

  override def executeCloseDayOrStopLoss(finInstrument: FinInstrument,
                                         position: Position,
                                         currentPrice: Double,
                                         stopLossPercentage: Int,
                                         tradeDateTime: ZonedDateTime,
                                         tradingExchange: TradingExchange,
                                         tradingMode: TradingMode): Option[Order] = executeStopLoss(
    finInstrument = finInstrument,
    position = position,
    currentPrice = currentPrice,
    stopLossPercentage = stopLossPercentage
  ) match
    case Some(value) => Some(value)
    case None =>
      executeCloseDay(
        finInstrument = finInstrument,
        position = position,
        currentPrice = currentPrice,
        tradeDateTime = tradeDateTime,
        tradingExchange = tradingExchange,
        tradingMode = tradingMode
      )

  private def executeCloseDay(
                               finInstrument: FinInstrument,
                               position: Position,
                               currentPrice: Double,
                               tradeDateTime: ZonedDateTime,
                               tradingExchange: TradingExchange,
                               tradingMode: TradingMode
                             ): Option[Order] =
    if TradingWindowValidator.shouldCloseDay(
      tradingDateTime = tradeDateTime,
      tradingMode = tradingMode,
      finInstrument = finInstrument,
      tradingExchange = tradingExchange
    )
    then
      logger.info(s"Close Day for Position : $position at $tradeDateTime")
      Some(
        Order(
          `type` = Sell,
          symbol = finInstrument.symbol,
          dateTime = tradeDateTime,
          shares = position.numberOfShares,
          price = currentPrice,
          positionId = Some(position.id),
          tradingStrategyType = position.tradingStrategyType,
          trigger = OrderTrigger.CloseDay
        )
      )
    else None

  private def executeStopLoss(
                               finInstrument: FinInstrument,
                               position: Position,
                               currentPrice: Double,
                               stopLossPercentage: Int
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
          tradingStrategyType = position.tradingStrategyType,
          trigger = OrderTrigger.StopLoss
        )
      )
    else None

object ForcePositionExitService:
   def apply(): ForcePositionExitService = new ForcePositionExitServiceImpl()
