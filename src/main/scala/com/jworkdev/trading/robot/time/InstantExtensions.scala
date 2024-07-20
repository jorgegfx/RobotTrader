package com.jworkdev.trading.robot.time

import java.time.{Instant, LocalDate, ZoneId}

object InstantExtensions:
  extension (instant: Instant)
    def isToday(zoneId: ZoneId = ZoneId.systemDefault()): Boolean =
      val instantDate = instant.atZone(zoneId).toLocalDate
      val currentDate = LocalDate.now(zoneId)
      instantDate == currentDate
