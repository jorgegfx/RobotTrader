package com.jworkdev.trading.robot.domain

case class Position(
    id: Long,
    openPricePerShare: Double,
    openDate: Instant,
    numberOfShares: Long,
    closeDate: Option[Instant],
    closePricePerShare: Option[Instant]
)
