package com.jworkdev.trading.robot

import java.time.{DayOfWeek, LocalDate, LocalDateTime, LocalTime, ZoneId, ZoneOffset, ZonedDateTime}

package object domain:

  import java.time.Instant

  case class Position(
      id: Long,
      symbol: String,
      numberOfShares: Long,
      openPricePerShare: Double,
      closePricePerShare: Option[Double],
      openDate: Instant,
      closeDate: Option[Instant],
      pnl: Option[Double],
      tradingStrategyType: TradingStrategyType
  ):
    def totalOpenPrice: Double = numberOfShares * openPricePerShare
    def totalClosePrice: Option[Double] = closePricePerShare.map(numberOfShares * _)
    def shouldExitForStopLoss(currentPricePerShare: Double, stopLossPercentage: Int): Boolean =
      val gain = currentPricePerShare - openPricePerShare
      if(gain < 0)
        val currentPercentage = (currentPricePerShare/openPricePerShare * 100).toInt
        val percentageLoss = 100 - currentPercentage
        percentageLoss > stopLossPercentage
      else false

  enum FinInstrumentType:
    case Stock, ETF, Crypto

  enum TradingStrategyType:
    case OpenGap, MACD

  case class FinInstrument(
      symbol: String,
      name: String,
      `type`: FinInstrumentType,
      volatility: Option[Double],
      exchange: String,
      creationDate: Instant,
      lastUpdate: Option[Instant],
      isActive: Boolean
  )

  case class TradingStrategy(
      `type`: TradingStrategyType,
      pnl: Option[Double]
  )
  enum TradingExchangeWindowType:
    case BusinessDaysWeek, Always

  case class TradingExchange(id: String,
                             name: String,
                             windowType: TradingExchangeWindowType,
                             openingTime: Option[LocalTime],
                             closingTime: Option[LocalTime],
                             timezone: Option[String]):
    private val nonBusinessDays = Set(DayOfWeek.SUNDAY,DayOfWeek.SATURDAY)

    private def createFromTime(localTime: LocalTime, timezone: String): LocalDateTime =
      val zoneId: ZoneId = ZoneId.of(timezone)
      val localDateTime: LocalDateTime = LocalDateTime.of(LocalDate.now(), localTime)
      val zonedDateTime: ZonedDateTime = ZonedDateTime.of(localDateTime, zoneId)
      val localZoneId: ZoneId = ZoneId.systemDefault()
      val localZonedDateTime = zonedDateTime.withZoneSameInstant(localZoneId)
      localZonedDateTime.toLocalDateTime

    def currentCloseWindow: Option[LocalDateTime] =
      for
        timezone <- timezone
        closingTime <- closingTime
      yield createFromTime(closingTime,timezone)  
      

    def currentOpenWindow: Option[LocalDateTime] =
      for
        timezone <- timezone
        openingTime <- openingTime
      yield createFromTime(openingTime,timezone)

    def isTradingExchangeDay(currentLocalTime: LocalDateTime): Boolean =
        windowType == TradingExchangeWindowType.Always ||
          !nonBusinessDays.contains(currentLocalTime.getDayOfWeek)

  case class Account(id: Long, name: String, balance: Double)
