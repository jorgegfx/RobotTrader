package com.jworkdev.trading.robot.service

import com.jworkdev.trading.robot.config.TradingMode
import com.jworkdev.trading.robot.domain.{FinInstrument, TradingExchange, TradingExchangeWindowType}

import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

object TradingWindowValidator:
  private val limitHoursBeforeCloseDay = 1

  def isNotOutOfBuyingWindow(tradingDateTime: LocalDateTime,
                             tradingMode: TradingMode,
                             finInstrument: FinInstrument,
                             tradingExchangeMap: Map[String, TradingExchange]): Boolean =
    (tradingMode == TradingMode.IntraDay &&
      isNotOutOfBuyingWindow(tradingDateTime=tradingDateTime,
        finInstrument = finInstrument,
        tradingExchangeMap = tradingExchangeMap)) || (tradingMode == TradingMode.Swing)

  private def isNotOutOfBuyingWindow(tradingDateTime: LocalDateTime,
                                     finInstrument: FinInstrument,
                                     tradingExchangeMap: Map[String, TradingExchange]): Boolean =
    tradingExchangeMap.get(finInstrument.exchange).exists(exchange => {
      exchange.windowType == TradingExchangeWindowType.Always ||
        isNotOutOfBuyingWindow(tradingDateTime = tradingDateTime, exchange = exchange)
    })

  private def isNotOutOfBuyingWindow(tradingDateTime: LocalDateTime,
                                     exchange: TradingExchange): Boolean =
    val res = for
      limitClosingTime <- exchange.closeWindow(tradingDateTime = tradingDateTime).
        map(_.minus(limitHoursBeforeCloseDay, ChronoUnit.HOURS))
      currentOpenWindow <- exchange.openWindow(tradingDateTime = tradingDateTime)
    yield tradingDateTime.isBefore(limitClosingTime) &&
      tradingDateTime.isAfter(currentOpenWindow)
    res.getOrElse(false) && exchange.isTradingExchangeDay(tradingDateTime = tradingDateTime)
