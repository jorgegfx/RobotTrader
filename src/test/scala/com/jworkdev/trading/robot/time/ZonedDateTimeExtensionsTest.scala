package com.jworkdev.trading.robot.time

import org.scalatest.funsuite.AnyFunSuiteLike

import java.time.{LocalDateTime, ZoneId, ZonedDateTime}
import com.jworkdev.trading.robot.time.ZonedDateTimeExtensions.isSameDay
class ZonedDateTimeExtensionsTest extends AnyFunSuiteLike:

  test("testIsSameDay") {
    val todayUTC = ZonedDateTime.of(LocalDateTime.of(2024,8,15,10,0),ZoneId.of("UTC"))
    val today = ZonedDateTime.of(LocalDateTime.of(2024,8,15,6,0),ZoneId.of("America/New_York"))
    assert(todayUTC.isSameDay(today))
  }

  test("testIsNotSameDay") {
    val todayUTC = ZonedDateTime.of(LocalDateTime.of(2024, 8, 15, 10, 0), ZoneId.of("UTC"))
    val today = ZonedDateTime.of(LocalDateTime.of(2024, 8, 16, 6, 0), ZoneId.of("America/New_York"))
    assert(!todayUTC.isSameDay(today))
  }

