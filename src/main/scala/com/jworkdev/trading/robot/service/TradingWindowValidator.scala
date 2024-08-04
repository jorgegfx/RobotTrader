package com.jworkdev.trading.robot.service

import com.jworkdev.trading.robot.config.TradingMode
import com.jworkdev.trading.robot.domain.{FinInstrument, TradingExchange, TradingExchangeWindowType}

import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

object TradingWindowValidator:
  private val limitHoursBeforeCloseDay = 1

  def isNotOutOfBuyingWindow(currentLocalTime: LocalDateTime,
                             tradingMode: TradingMode,
                             finInstrument: FinInstrument,
                             tradingExchangeMap: Map[String, TradingExchange]): Boolean =
    tradingMode == TradingMode.IntraDay &&
      isNotOutOfBuyingWindow(currentLocalTime=currentLocalTime,
        finInstrument = finInstrument,
        tradingExchangeMap = tradingExchangeMap)

  private def isNotOutOfBuyingWindow(currentLocalTime: LocalDateTime,
                                     finInstrument: FinInstrument,
                                     tradingExchangeMap: Map[String, TradingExchange]): Boolean =
    tradingExchangeMap.get(finInstrument.exchange).exists(exchange => {
      exchange.windowType == TradingExchangeWindowType.Always ||
        isNotOutOfBuyingWindow(currentLocalTime = currentLocalTime, exchange = exchange)
    })

  private def isNotOutOfBuyingWindow(currentLocalTime: LocalDateTime,
                                     exchange: TradingExchange): Boolean =
    val res = for
      limitClosingTime <- exchange.currentCloseWindow(currentDateTime = currentLocalTime).
        map(_.minus(limitHoursBeforeCloseDay, ChronoUnit.HOURS))
      currentOpenWindow <- exchange.currentOpenWindow(currentDateTime = currentLocalTime)
    yield currentLocalTime.isBefore(limitClosingTime) &&
      currentLocalTime.isAfter(currentOpenWindow)
    res.getOrElse(false) && exchange.isTradingExchangeDay(currentLocalTime = currentLocalTime)
