package com.jworkdev.trading.robot.time

import org.scalatest.funsuite.AnyFunSuiteLike

import java.time.{Instant, LocalDateTime}
import com.jworkdev.trading.robot.time.InstantExtensions.isToday
import com.jworkdev.trading.robot.time.InstantExtensions.isSameDay
class InstantExtensionsTest extends AnyFunSuiteLike:

  test("testIsToday") {
    val now = Instant.now()
    assert(now.isToday())
  }

  test("testIsSameDay") {
    val now = Instant.now()
    val localDateTime = LocalDateTime.now()
    assert(now.isSameDay(localDateTime = localDateTime))
  }
