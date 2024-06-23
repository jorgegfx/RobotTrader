package com.jworkdev.trading.robot

import java.util.Date

package object domain {

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
                     ) {
    def openDateSql(): Date = Date.from(openDate)
  }
}
