package com.jworkdev.trading.robot.service

import com.jworkdev.trading.robot.config.TradingMode.IntraDay
import com.jworkdev.trading.robot.domain.FinInstrumentType.Stock
import com.jworkdev.trading.robot.domain.{FinInstrument, Position, TradingExchange, TradingStrategyType}
import com.jworkdev.trading.robot.domain.TradingExchangeWindowType.BusinessDaysWeek
import org.scalatest.funsuite.AnyFunSuiteLike
import com.jworkdev.trading.robot.time.LocalDateTimeExtensions.toZonedDateTime

import java.time.temporal.ChronoUnit
import java.time.{LocalDateTime, LocalTime, ZonedDateTime}

class ForcePositionExitServiceTest extends AnyFunSuiteLike:
  private val forcePositionExitService = ForcePositionExitService()
  private val symbol = "BFI"
  private val exchangeName = "NASDAQ"
  private val exchange = TradingExchange(
    id = exchangeName,
    name = exchangeName,
    windowType = BusinessDaysWeek,
    openingTime = Some(LocalTime.of(9, 0)),
    closingTime = Some(LocalTime.of(16, 0)),
    timezone = Some("America/New_York")
  )
  private val finInstrument = FinInstrument(
    symbol = symbol,
    name = "",
    `type` = Stock,
    priceVolatility = Some(1.0),
    averageDailyVolume = None,
    preMarketGap = None,
    preMarketNumberOfShareTrades = None,
    averageTrueRange = None,
    exchange = exchangeName,
    creationDate = ZonedDateTime.now(),
    lastUpdate = None,
    isActive = true
  )
  test("testExecuteCloseDayOrStopLoss Close Day") {
    val tradingPrice = 110
    val tradingTime = LocalDateTime.of(2024, 8, 9, 15, 35).toZonedDateTime
    val position = Position(
      id = 1,
      symbol = symbol,
      numberOfShares = 2,
      openPricePerShare = 100,
      closePricePerShare = None,
      openDate = tradingTime.minus(1, ChronoUnit.HOURS),
      closeDate = None,
      pnl = None,
      tradingStrategyType = TradingStrategyType.OpenGap
    )
    val res = forcePositionExitService.executeCloseDayOrStopLoss(finInstrument = finInstrument,
      position = position,
      currentPrice = tradingPrice,
      stopLossPercentage = 10,
      tradeDateTime = tradingTime,
      tradingExchange = exchange,
      tradingMode = IntraDay)
    assert(res.isDefined)
  }

  test("testExecuteCloseDayOrStopLoss Not Close Day") {
    val tradingPrice = 110
    val tradingTime = LocalDateTime.of(2024, 8, 9, 10, 35).toZonedDateTime
    val position = Position(
      id = 1,
      symbol = symbol,
      numberOfShares = 2,
      openPricePerShare = 100,
      closePricePerShare = None,
      openDate = tradingTime.minus(1, ChronoUnit.HOURS),
      closeDate = None,
      pnl = None,
      tradingStrategyType = TradingStrategyType.OpenGap
    )
    val res = forcePositionExitService.executeCloseDayOrStopLoss(finInstrument = finInstrument,
      position = position,
      currentPrice = tradingPrice,
      stopLossPercentage = 10,
      tradeDateTime = tradingTime,
      tradingExchange = exchange,
      tradingMode = IntraDay)
    assert(res.isEmpty)
  }

  test("testExecuteCloseDayOrStopLoss StopLoss Exit") {
    val tradingPrice = 70
    val tradingTime = LocalDateTime.of(2024, 8, 9, 11, 35).toZonedDateTime
    val position = Position(
      id = 1,
      symbol = symbol,
      numberOfShares = 2,
      openPricePerShare = 100,
      closePricePerShare = None,
      openDate = tradingTime.minus(1, ChronoUnit.HOURS),
      closeDate = None,
      pnl = None,
      tradingStrategyType = TradingStrategyType.OpenGap
    )
    val res = forcePositionExitService.executeCloseDayOrStopLoss(finInstrument = finInstrument,
      position = position,
      currentPrice = tradingPrice,
      stopLossPercentage = 10,
      tradeDateTime = tradingTime,
      tradingExchange = exchange,
      tradingMode = IntraDay)
    assert(res.isDefined)
  }

  test("testExecuteCloseDayOrStopLoss StopLoss No Exit") {
    val tradingPrice = 120
    val tradingTime = LocalDateTime.of(2024, 8, 9, 11, 35).toZonedDateTime
    val position = Position(
      id = 1,
      symbol = symbol,
      numberOfShares = 2,
      openPricePerShare = 100,
      closePricePerShare = None,
      openDate = tradingTime.minus(1, ChronoUnit.HOURS),
      closeDate = None,
      pnl = None,
      tradingStrategyType = TradingStrategyType.OpenGap
    )
    val res = forcePositionExitService.executeCloseDayOrStopLoss(finInstrument = finInstrument,
      position = position,
      currentPrice = tradingPrice,
      stopLossPercentage = 10,
      tradeDateTime = tradingTime,
      tradingExchange = exchange,
      tradingMode = IntraDay)
    assert(res.isEmpty)
  }
