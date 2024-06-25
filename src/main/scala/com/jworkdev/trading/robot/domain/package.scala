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
      pnl: Option[Double]
  )
  enum FinInstrumentType:
    case Stock, Crypto

  case class FinInstrumentConfig(
      symbol: String,
      pnl: Option[Double],
      finInstrumentType: FinInstrumentType,
      lastPnlUpdate: Option[Instant],
  )
