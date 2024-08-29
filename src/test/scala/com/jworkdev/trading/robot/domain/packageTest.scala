package com.jworkdev.trading.robot.domain

import com.jworkdev.trading.robot.domain.TradingExchangeWindowType.BusinessDaysWeek
import org.scalatest.flatspec.AnyFlatSpec

import java.time.temporal.ChronoUnit
import java.time.{LocalDateTime, LocalTime}
import com.jworkdev.trading.robot.time.LocalDateTimeExtensions.toZonedDateTime

class packageTest extends AnyFlatSpec:
  it should "be between the window" in {
    val currentTime = LocalTime.now()
    val tradingDateTime = LocalDateTime.of(2024,8,15,currentTime.getHour,currentTime.getMinute).toZonedDateTime
    val tradingExchange = TradingExchange(
      id = "TEST",
      name = "Test",
      openingTime = Some(currentTime.minus(2, ChronoUnit.HOURS)),
      closingTime = Some(currentTime.plus(3, ChronoUnit.HOURS)),
      timezone = Some("America/New_York"),
      windowType = BusinessDaysWeek
    )
    val currentCloseWindowOpt = tradingExchange.closeWindow(tradingDateTime = tradingDateTime)
    assert(currentCloseWindowOpt.isDefined)
    val currentCloseWindow = currentCloseWindowOpt.get
    assert(tradingDateTime.isBefore(currentCloseWindow))
    assert(tradingDateTime.plus(4,ChronoUnit.HOURS).isAfter(currentCloseWindow))
  }
