package com.jworkdev.trading.robot.domain

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
      openingTime = currentTime.minus(1, ChronoUnit.HOURS),
      closingTime = currentTime.plus(2, ChronoUnit.HOURS),
      timezone = "America/New_York"
    )
    val currentCloseWindow = tradingExchange.currentCloseWindow
    assert(now.isBefore(currentCloseWindow))
    assert(now.plus(3,ChronoUnit.HOURS).isAfter(currentCloseWindow))
  }
