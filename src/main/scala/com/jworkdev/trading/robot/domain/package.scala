package com.jworkdev.trading.robot

import com.jworkdev.trading.robot.market.data.StockPrice

import java.time.{DayOfWeek, LocalDate, LocalDateTime, LocalTime, ZoneId, ZoneOffset, ZonedDateTime}
import com.jworkdev.trading.robot.time.InstantExtensions.toLocalDateTime
package object domain:

  def groupPricesByDate(prices: List[StockPrice]): Map[LocalDate, List[StockPrice]] =
    prices
      .map(price => (price, price.snapshotTime.toLocalDateTime().toLocalDate))
      .groupBy(_._2)
      .map { case (key, value) =>
        (key, value.map(_._1))
      }

  import java.time.ZonedDateTime

  case class Position(
      id: Long,
      symbol: String,
      numberOfShares: Long,
      openPricePerShare: Double,
      closePricePerShare: Option[Double],
      openDate: ZonedDateTime,
      closeDate: Option[ZonedDateTime],
      pnl: Option[Double],
      tradingStrategyType: TradingStrategyType
  ):
    def totalOpenPrice: Double = numberOfShares * openPricePerShare
    def totalClosePrice: Option[Double] = closePricePerShare.map(numberOfShares * _)
    def shouldExitForStopLoss(currentPricePerShare: Double, stopLossPercentage: Int): Boolean =
      val gain = currentPricePerShare - openPricePerShare
      if gain < 0 then
        val currentPercentage = (currentPricePerShare / openPricePerShare * 100).toInt
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
      creationDate: ZonedDateTime,
      lastUpdate: Option[ZonedDateTime],
      isActive: Boolean
  )

  case class TradingStrategy(
      `type`: TradingStrategyType,
      pnl: Option[Double]
  )
  enum TradingExchangeWindowType:
    case BusinessDaysWeek, Always

  case class TradingExchange(
      id: String,
      name: String,
      windowType: TradingExchangeWindowType,
      openingTime: Option[LocalTime],
      closingTime: Option[LocalTime],
      timezone: Option[String]
  ):
    private val nonBusinessDays = Set(DayOfWeek.SUNDAY, DayOfWeek.SATURDAY)

    private def createFromTime(tradingDateTime: LocalDateTime, localTime: LocalTime, timezone: String): LocalDateTime =
      val zoneId: ZoneId = ZoneId.of(timezone)
      val localDateTime: LocalDateTime = LocalDateTime.of(tradingDateTime.toLocalDate, localTime)
      val zonedDateTime: ZonedDateTime = ZonedDateTime.of(localDateTime, zoneId)
      val localZoneId: ZoneId = ZoneId.systemDefault()
      val localZonedDateTime = zonedDateTime.withZoneSameInstant(localZoneId)
      localZonedDateTime.toLocalDateTime

    def closeWindow(tradingDateTime: LocalDateTime): Option[LocalDateTime] =
      for
        timezone <- timezone
        closingTime <- closingTime
      yield createFromTime(tradingDateTime = tradingDateTime, localTime = closingTime, timezone = timezone)

    def openWindow(tradingDateTime: LocalDateTime): Option[LocalDateTime] =
      for
        timezone <- timezone
        openingTime <- openingTime
      yield createFromTime(tradingDateTime = tradingDateTime, localTime = openingTime, timezone = timezone)

    def isTradingExchangeDay(tradingDateTime: LocalDateTime): Boolean =
      windowType == TradingExchangeWindowType.Always ||
        !nonBusinessDays.contains(tradingDateTime.getDayOfWeek)

  case class Account(id: Long, name: String, balance: Double)
