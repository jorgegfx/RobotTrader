package com.jworkdev.trading.robot.domain

import com.jworkdev.trading.robot.domain.TradingExchangeWindowType.BusinessDaysWeek
import org.scalatest.flatspec.AnyFlatSpec

import java.time.temporal.ChronoUnit
import java.time.{Instant, LocalDate, LocalDateTime, LocalTime, ZoneId, ZonedDateTime}

class packageTest extends AnyFlatSpec:
  it should "be between the window" in {
    val currentTime = LocalTime.now()
    val now = LocalDateTime.now()
    val tradingExchange = TradingExchange(
      id = "TEST",
      name = "Test",
      openingTime = Some(currentTime.minus(1, ChronoUnit.HOURS)),
      closingTime = Some(currentTime.plus(2, ChronoUnit.HOURS)),
      timezone = Some("America/New_York"),
      windowType = BusinessDaysWeek
    )
    val currentCloseWindowOpt = tradingExchange.currentCloseWindow
    assert(currentCloseWindowOpt.isDefined)
    val currentCloseWindow = currentCloseWindowOpt.get
    assert(now.isBefore(currentCloseWindow))
    assert(now.plus(3,ChronoUnit.HOURS).isAfter(currentCloseWindow))
  }
