package com.jworkdev.trading.robot

import com.jworkdev.trading.robot.market.data.StockPrice

import java.time.*
package object domain:

  def getAverage[T](numbers: List[T])(implicit num: Numeric[T]): Double = {
    if (numbers.nonEmpty) {
      num.toDouble(numbers.sum) / numbers.size
    } else {
      0.0
    }
  }

  def groupPricesByDate(prices: List[StockPrice]): Map[LocalDate, List[StockPrice]] =
    prices
      .map(price => (price, price.snapshotTime.toLocalDate))
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
      priceVolatility: Option[Double],
      averageDailyVolume: Option[Double],
      preMarketGap: Option[Double],
      preMarketNumberOfShareTrades: Option[Double],
      averageTrueRange: Option[Double],
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

    def closeWindow(tradingDateTime: ZonedDateTime): Option[ZonedDateTime] =
      for
        timezone <- timezone
        closingTime <- closingTime
      yield createFromZonedDateTime(tradingDateTime = tradingDateTime, localTime = closingTime, timezone = timezone)

    def openWindow(tradingDateTime: ZonedDateTime): Option[ZonedDateTime] =
      for
        timezone <- timezone
        openingTime <- openingTime
      yield createFromZonedDateTime(tradingDateTime = tradingDateTime, localTime = openingTime, timezone = timezone)

    private def createFromZonedDateTime(
        tradingDateTime: ZonedDateTime,
        localTime: LocalTime,
        timezone: String
    ): ZonedDateTime =
      LocalDateTime.of(tradingDateTime.toLocalDate, localTime).atZone(ZoneId.of(timezone))

    def isTradingExchangeDay(tradingDateTime: ZonedDateTime): Boolean =
      windowType == TradingExchangeWindowType.Always ||
        !nonBusinessDays.contains(tradingDateTime.getDayOfWeek)

  case class Account(id: Long, name: String, balance: Double)

  case class PnLPerformance(entryDate: LocalDate, tradingStrategyType: TradingStrategyType, amount: Double)
