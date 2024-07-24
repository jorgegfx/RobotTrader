package com.jworkdev.trading.robot

import java.time.LocalTime

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

  case class TradingExchange(id: String,
                             name: String,
                             openingTime: LocalTime,
                             closingTime: LocalTime)

  case class Account(id: Long, name: String, balance: Double)
