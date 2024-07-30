package com.jworkdev.trading.robot.time

import java.time.{Instant, LocalDate, LocalDateTime, ZoneId, ZonedDateTime}

object InstantExtensions:
  extension (instant: Instant)
    def isToday(zoneId: ZoneId = ZoneId.systemDefault()): Boolean =
      val instantDate = instant.atZone(zoneId).toLocalDate
      val currentDate = LocalDate.now(zoneId)
      instantDate == currentDate

object LocalDateTimeExtensions:
  extension(localDateTime: LocalDateTime)
    def toZonedDateTime: ZonedDateTime =
      localDateTime.atZone(ZoneId.systemDefault())
