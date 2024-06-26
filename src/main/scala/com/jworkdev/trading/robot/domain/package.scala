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
  ){
    def totalOpenPrice: Double = numberOfShares * openPricePerShare
    def totalClosePrice: Option[Double] = closePricePerShare.map(numberOfShares * _ ) 
  }
  enum FinInstrumentType:
    case Stock, Crypto

  case class FinInstrumentConfig(
      symbol: String,
      pnl: Option[Double],
      finInstrumentType: FinInstrumentType,
      lastPnlUpdate: Option[Instant],
  )

  case class Account(id: Long, name: String, balance: Double)
