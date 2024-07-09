package com.jworkdev.trading.robot

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

  enum FinInstrumentType:
    case Stock, Crypto

  enum TradingStrategyType:
    case OpenGap, MACD

  case class FinInstrument(symbol: String, finInstrumentType: FinInstrumentType)

  case class TradingStrategy(
      `type`: TradingStrategyType,
      pnl: Option[Double]
  )

  case class Account(id: Long, name: String, balance: Double)
