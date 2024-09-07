package com.jworkdev.trading.robot.service

import com.jworkdev.trading.robot.config.TradingMode
import com.jworkdev.trading.robot.domain.{FinInstrument, TradingExchange, TradingExchangeWindowType}

import java.time.{LocalDateTime, ZonedDateTime}
import java.time.temporal.ChronoUnit

object TradingWindowValidator:
  private val limitHoursBeforeCloseDay = 1
  private val minutesBeforeToCloseDay = 30

  def shouldCloseDay(tradingDateTime: ZonedDateTime,
                     tradingMode: TradingMode,
                     finInstrument: FinInstrument,
                     tradingExchange: TradingExchange): Boolean =
    val lastChanceToCloseTime = for
      limitClosingTime <- tradingExchange.closeWindow(tradingDateTime = tradingDateTime)
        .map(_.minus(minutesBeforeToCloseDay, ChronoUnit.MINUTES))
    yield limitClosingTime
    tradingMode == TradingMode.IntraDay && 
      lastChanceToCloseTime.exists(closeTime => tradingDateTime.isAfter(closeTime))
        

  def isNotOutOfBuyingWindow(tradingDateTime: ZonedDateTime,
                             tradingMode: TradingMode,
                             finInstrument: FinInstrument,
                             tradingExchange: TradingExchange): Boolean =
    (tradingMode == TradingMode.IntraDay &&
      isNotOutOfBuyingWindow(tradingDateTime = tradingDateTime,
        finInstrument = finInstrument,
        tradingExchange = tradingExchange)) || (tradingMode == TradingMode.Swing)

  private def isNotOutOfBuyingWindow(tradingDateTime: ZonedDateTime,
                                     finInstrument: FinInstrument,
                                     tradingExchange: TradingExchange): Boolean =
    tradingExchange.windowType == TradingExchangeWindowType.Always ||
      isNotOutOfBuyingWindow(tradingDateTime = tradingDateTime, exchange = tradingExchange)


  private def isNotOutOfBuyingWindow(tradingDateTime: ZonedDateTime,
                                     finInstrument: FinInstrument,
                                     tradingExchangeMap: Map[String, TradingExchange]): Boolean =
    tradingExchangeMap.get(finInstrument.exchange).exists(exchange => {
      exchange.windowType == TradingExchangeWindowType.Always ||
        isNotOutOfBuyingWindow(tradingDateTime = tradingDateTime, exchange = exchange)
    })

  private def isNotOutOfBuyingWindow(tradingDateTime: ZonedDateTime,
                                     exchange: TradingExchange): Boolean =
    val res = for
      limitClosingTime <- exchange.closeWindow(tradingDateTime = tradingDateTime).
        map(_.minus(limitHoursBeforeCloseDay, ChronoUnit.HOURS))
      currentOpenWindow <- exchange.openWindow(tradingDateTime = tradingDateTime)
    yield tradingDateTime.isBefore(limitClosingTime) &&
      tradingDateTime.isAfter(currentOpenWindow)
    res.getOrElse(false) && exchange.isTradingExchangeDay(tradingDateTime = tradingDateTime)
