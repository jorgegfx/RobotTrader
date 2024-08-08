package com.jworkdev.trading.robot.service

import com.jworkdev.trading.robot.config.TradingMode.IntraDay
import com.jworkdev.trading.robot.domain.TradingExchangeWindowType.{Always, BusinessDaysWeek}
import com.jworkdev.trading.robot.domain.{FinInstrument, FinInstrumentType, TradingExchange}
import org.scalatest.funsuite.AnyFunSuiteLike

import java.time.{Instant, LocalDate, LocalDateTime, LocalTime}

class TradingWindowValidatorTest extends AnyFunSuiteLike:
  private val localTime = LocalTime.of(8, 10)
  private val localDate = LocalDate.of(2024, 7, 27)
  private val localDateTime = LocalDateTime.of(localDate, localTime)

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
    val res = TradingWindowValidator.
      isNotOutOfBuyingWindow(tradingDateTime = LocalDateTime.now(),
        tradingMode = IntraDay, finInstrument = FinInstrument(
          symbol = "BTH",
          name = "BitCoin",
          `type` = FinInstrumentType.Stock,
          exchange = "CRYPTO",
          volatility = Some(10d),
          creationDate = Instant.now(),
          lastUpdate = None,
          isActive = true
        ),
        tradingExchangeMap = Map(
          "CRYPTO" -> cryptoExchange
        ))
    assert(res)
  }

  test("is Not Business Day") {
    val localTime = LocalTime.of(8,10)
    val localDate =  LocalDate.of(2024,7,27)
    val localDateTime = LocalDateTime.of(localDate,localTime)
    val res = TradingWindowValidator.
      isNotOutOfBuyingWindow(tradingDateTime = localDateTime,
        tradingMode = IntraDay, finInstrument = FinInstrument(
          symbol = "NVDA",
          name = "Nvidia",
          `type` = FinInstrumentType.Stock,
          exchange = "NASDAQ",
          volatility = Some(10d),
          creationDate = Instant.now(),
          lastUpdate = None,
          isActive = true
        ),
        tradingExchangeMap = Map(
          "NASDAQ" -> nasdaqExchange
        ))
    assert(!res)
  }

  test("before Trading window") {
    val localTime = LocalTime.of(8, 10)
    val localDate = LocalDate.of(2024, 7, 24)
    val localDateTime = LocalDateTime.of(localDate, localTime)
    val res = TradingWindowValidator.
      isNotOutOfBuyingWindow(tradingDateTime = localDateTime,
        tradingMode = IntraDay, finInstrument = FinInstrument(
          symbol = "NVDA",
          name = "Nvidia",
          `type` = FinInstrumentType.Stock,
          exchange = "NASDAQ",
          volatility = Some(10d),
          creationDate = Instant.now(),
          lastUpdate = None,
          isActive = true
        ),
        tradingExchangeMap = Map(
          "NASDAQ" -> nasdaqExchange
        ))
    assert(!res)
  }

  test("after Trading window") {
    val localTime = LocalTime.of(17, 10)
    val localDate = LocalDate.of(2024, 7, 24)
    val localDateTime = LocalDateTime.of(localDate, localTime)
    val res = TradingWindowValidator.
      isNotOutOfBuyingWindow(tradingDateTime = localDateTime,
        tradingMode = IntraDay, finInstrument = FinInstrument(
          symbol = "NVDA",
          name = "Nvidia",
          `type` = FinInstrumentType.Stock,
          exchange = "NASDAQ",
          volatility = Some(10d),
          creationDate = Instant.now(),
          lastUpdate = None,
          isActive = true
        ),
        tradingExchangeMap = Map(
          "NASDAQ" -> nasdaqExchange
        ))
    assert(!res)
  }

  test("between Trading window") {
    val localTime = LocalTime.of(12, 10)
    val localDate = LocalDate.of(2024, 7, 24)
    val localDateTime = LocalDateTime.of(localDate, localTime)
    val res = TradingWindowValidator.
      isNotOutOfBuyingWindow(tradingDateTime = localDateTime,
        tradingMode = IntraDay, finInstrument = FinInstrument(
          symbol = "NVDA",
          name = "Nvidia",
          `type` = FinInstrumentType.Stock,
          exchange = "NASDAQ",
          volatility = Some(10d),
          creationDate = Instant.now(),
          lastUpdate = None,
          isActive = true
        ),
        tradingExchangeMap = Map(
          "NASDAQ" -> nasdaqExchange
        ))
    assert(res)
  }
