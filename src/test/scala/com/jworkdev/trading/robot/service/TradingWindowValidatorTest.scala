package com.jworkdev.trading.robot.service

import com.jworkdev.trading.robot.config.TradingMode.IntraDay
import com.jworkdev.trading.robot.domain.TradingExchangeWindowType.{Always, BusinessDaysWeek}
import com.jworkdev.trading.robot.domain.{FinInstrument, FinInstrumentType, TradingExchange}
import org.scalatest.funsuite.AnyFunSuiteLike

import java.time.temporal.ChronoUnit
import java.time.{Instant, LocalDateTime, LocalTime}

class TradingWindowValidatorTest extends AnyFunSuiteLike:

  test("is Always") {
    val res = TradingWindowValidator.
      isNotOutOfBuyingWindow(currentLocalTime = LocalDateTime.now(),
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
          "NASDAQ" -> TradingExchange(
            id = "NASDAQ",
            name = "NASDAQ",
            openingTime = None,
            closingTime = None,
            timezone = None,
            windowType = Always
          )
        ))
    assert(res)
  }

  test("is Not Business Day") {
    val res = TradingWindowValidator.
      isNotOutOfBuyingWindow(currentLocalTime = LocalDateTime.now(),
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
          "NASDAQ" -> TradingExchange(
            id = "NASDAQ",
            name = "NASDAQ",
            openingTime = Some(LocalTime.now().minus(1, ChronoUnit.HOURS)),
            closingTime = Some(LocalTime.now().plus(2, ChronoUnit.HOURS)),
            timezone = Some("America/New_York"),
            windowType = BusinessDaysWeek
          )
        ))
    assert(res)
  }
