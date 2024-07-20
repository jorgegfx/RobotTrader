package com.jworkdev.trading.robot.time

import org.scalatest.funsuite.AnyFunSuiteLike

import java.time.Instant
import com.jworkdev.trading.robot.time.InstantExtensions.isToday
class InstantExtensionsTest extends AnyFunSuiteLike:

  test("testIsToday") {
    val now = Instant.now()
    assert(now.isToday())
  }
