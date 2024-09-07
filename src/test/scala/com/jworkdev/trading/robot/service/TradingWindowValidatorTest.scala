package com.jworkdev.trading.robot.service

import com.jworkdev.trading.robot.config.TradingMode
import com.jworkdev.trading.robot.config.TradingMode.IntraDay
import com.jworkdev.trading.robot.domain.TradingExchangeWindowType.{Always, BusinessDaysWeek}
import com.jworkdev.trading.robot.domain.{FinInstrument, FinInstrumentType, TradingExchange}
import org.scalatest.funsuite.AnyFunSuiteLike
import com.jworkdev.trading.robot.time.LocalDateTimeExtensions.toZonedDateTime

import java.time.{LocalDate, LocalDateTime, LocalTime, ZoneId, ZonedDateTime}

class TradingWindowValidatorTest extends AnyFunSuiteLike:
  private val localTime = LocalTime.of(8, 10)
  private val localDate = LocalDate.of(2024, 7, 27)
  private val localDateTime = LocalDateTime.of(localDate, localTime).toZonedDateTime
  private val finInstrument = FinInstrument(
    symbol = "NVDA",
    name = "Nvidia",
    `type` = FinInstrumentType.Stock,
    exchange = "NASDAQ",
    volatility = Some(10d),
    creationDate = ZonedDateTime.now(),
    lastUpdate = None,
    isActive = true
  )

  private val nasdaqExchange = TradingExchange(
    id = "NASDAQ",
    name = "NASDAQ",
    openingTime = Some(LocalTime.of(9, 30)),
    closingTime = Some(LocalTime.of(16, 0)),
    timezone = Some("America/New_York"),
    windowType = BusinessDaysWeek
  )
  private val cryptoExchange = TradingExchange(
    id = "CRYPTO",
    name = "CRYPTO",
    openingTime = None,
    closingTime = None,
    timezone = None,
    windowType = Always
  )

  test("is Always") {
    val finInstrument = FinInstrument(
      symbol = "BTH",
      name = "BitCoin",
      `type` = FinInstrumentType.Stock,
      exchange = "CRYPTO",
      volatility = Some(10d),
      creationDate = ZonedDateTime.now(),
      lastUpdate = None,
      isActive = true
    )
    val cryptoExchange = TradingExchange(
      id = "CRYPTO",
      name = "CRYPTO",
      openingTime = None,
      closingTime = None,
      timezone = None,
      windowType = Always
    )
    val res = TradingWindowValidator.isNotOutOfBuyingWindow(
      tradingDateTime = ZonedDateTime.now(),
      tradingMode = TradingMode.IntraDay,
      finInstrument = finInstrument,
      tradingExchange = cryptoExchange
    )
    assert(res)
  }

  test("is Not Business Day") {
    val localTime = LocalTime.of(8, 10)
    val localDate = LocalDate.of(2024, 7, 27)
    val localDateTime = LocalDateTime.of(localDate, localTime).toZonedDateTime
    val res = TradingWindowValidator.isNotOutOfBuyingWindow(
      tradingDateTime = localDateTime,
      tradingMode = IntraDay,
      finInstrument = finInstrument,
      tradingExchange = nasdaqExchange
    )
    assert(!res)
  }

  test("before Trading window") {
    val localTime = LocalTime.of(8, 10)
    val localDate = LocalDate.of(2024, 7, 24)
    val localDateTime = LocalDateTime.of(localDate, localTime).toZonedDateTime
    val res = TradingWindowValidator.isNotOutOfBuyingWindow(
      tradingDateTime = localDateTime,
      tradingMode = IntraDay,
      finInstrument = finInstrument,
      tradingExchange = nasdaqExchange
    )
    assert(!res)
  }

  test("after Trading window") {
    val localTime = LocalTime.of(17, 10)
    val localDate = LocalDate.of(2024, 7, 24)
    val localDateTime = LocalDateTime.of(localDate, localTime).toZonedDateTime
    val res = TradingWindowValidator.isNotOutOfBuyingWindow(
      tradingDateTime = localDateTime,
      tradingMode = IntraDay,
      finInstrument = finInstrument,
      tradingExchange = nasdaqExchange
    )
    assert(!res)
  }

  test("between Trading window") {
    val localTime = LocalTime.of(12, 10)
    val localDate = LocalDate.of(2024, 7, 24)
    val localDateTime = LocalDateTime.of(localDate, localTime).toZonedDateTime
    val res = TradingWindowValidator.isNotOutOfBuyingWindow(
      tradingDateTime = localDateTime,
      tradingMode = IntraDay,
      finInstrument = finInstrument,
      tradingExchange = nasdaqExchange
    )
    assert(res)
  }

  test("before shouldCloseDay") {
    val localTime = LocalTime.of(12, 10)
    val localDate = LocalDate.of(2024, 7, 24)
    val localDateTime = LocalDateTime.of(localDate, localTime).toZonedDateTime
    val res = TradingWindowValidator.shouldCloseDay(
      tradingDateTime = localDateTime,
      tradingMode = IntraDay,
      finInstrument = finInstrument,
      tradingExchange = nasdaqExchange
    )
    assert(!res)
  }

  test("before shouldCloseDay another timezone") {
    val localTime = LocalTime.of(16, 10)
    val localDate = LocalDate.of(2024, 7, 24)
    val localDateTime = LocalDateTime.of(localDate, localTime).atZone(ZoneId.of("UTC"))
    val res = TradingWindowValidator.shouldCloseDay(
      tradingDateTime = localDateTime,
      tradingMode = IntraDay,
      finInstrument = finInstrument,
      tradingExchange = nasdaqExchange
    )
    assert(!res)
  }

  test("after shouldCloseDay") {
    val localTime = LocalTime.of(15, 35)
    val localDate = LocalDate.of(2024, 7, 24)
    val localDateTime = LocalDateTime.of(localDate, localTime).toZonedDateTime
    val res = TradingWindowValidator.shouldCloseDay(
      tradingDateTime = localDateTime,
      tradingMode = IntraDay,
      finInstrument = finInstrument,
      tradingExchange = nasdaqExchange
    )
    assert(res)
  }

  test("testWindowZone for isNotOutOfBuyingWindow") {
    val todayUTC = ZonedDateTime.of(LocalDateTime.of(2024, 8, 15, 17, 0), ZoneId.of("UTC"))
    val res = TradingWindowValidator.isNotOutOfBuyingWindow(
      tradingDateTime = todayUTC,
      tradingMode = IntraDay,
      finInstrument = finInstrument,
      tradingExchange = nasdaqExchange
    )
    assert(res)
  }
  test("testWindowZone for isOutOfBuyingWindow") {
    val todayUTC = ZonedDateTime.of(LocalDateTime.of(2024, 8, 15, 21, 0), ZoneId.of("UTC"))
    val res = TradingWindowValidator.isNotOutOfBuyingWindow(
      tradingDateTime = todayUTC,
      tradingMode = IntraDay,
      finInstrument = finInstrument,
      tradingExchange = nasdaqExchange
    )
    assert(!res)
  }
