package com.jworkdev.trading.robot.time

import java.time.{Instant, LocalDate, LocalDateTime, ZoneId, ZonedDateTime}

object InstantExtensions:
  extension (instant: Instant)
    def isToday(zoneId: ZoneId = ZoneId.systemDefault()): Boolean =
      val instantDate = instant.atZone(zoneId).toLocalDate
      val currentDate = LocalDate.now(zoneId)
      instantDate == currentDate

    def isSameDay(zoneId: ZoneId = ZoneId.systemDefault(),localDateTime: LocalDateTime): Boolean =
      val instantDate = instant.atZone(zoneId).toLocalDate
      localDateTime.toLocalDate.equals(instantDate)
      
    def toLocalDateTime(zoneId: ZoneId = ZoneId.systemDefault()): LocalDateTime =
      instant.atZone(zoneId).toLocalDateTime

object LocalDateTimeExtensions:
  extension(localDateTime: LocalDateTime)
    def toZonedDateTime: ZonedDateTime =
      localDateTime.atZone(ZoneId.systemDefault())

    def isSameDay(other: LocalDateTime): Boolean =
      localDateTime.toLocalDate.equals(other)


object ZonedDateTimeExtensions:
  extension(zonedDateTime: ZonedDateTime)
    
    def isSameDay(localDateTime: LocalDateTime): Boolean =
      zonedDateTime.toLocalDate.equals(localDateTime)
