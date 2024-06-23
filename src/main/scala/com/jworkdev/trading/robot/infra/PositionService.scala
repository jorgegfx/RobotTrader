package com.jworkdev.trading.robot.infra

import com.jworkdev.trading.robot.domain.Position
import doobie.implicits.*
import io.github.gaelrenoux.tranzactio.doobie
import io.github.gaelrenoux.tranzactio.doobie.{TranzactIO, tzio}

import java.time.Instant
import java.util.Date

trait PositionService:
  def create(position: Position): TranzactIO[Unit]

  def findOpenBetween(from: Instant, to: Instant): TranzactIO[List[Position]]

  def findCloseBetween(from: Instant, to: Instant): TranzactIO[List[Position]]

  def getPnL(from: Instant, to: Instant): TranzactIO[Double]

class PositionServiceImpl extends PositionService:
  override def create(position: Position): TranzactIO[Unit] = tzio {
    sql"""INSERT INTO position (symbol,number_of_shares,open_date,open_price_per_share)
         VALUES (${position.symbol},
         ${position.numberOfShares},
         ${position.openDateSql()},
         ${position.openPricePerShare})""".update.run
      .map(_ => ())
  }

  private case class PositionDB(
      id: Long,
      symbol: String,
      numberOfShares: Long,
      openPricePerShare: Double,
      closePricePerShare: Double,
      openDate: Date,
      closeDate: Date,
      pnl: Double
  ):
    def toDomain: Position = Position(
      id = id,
      symbol = symbol,
      openPricePerShare = openPricePerShare,
      openDate = openDate.toInstant,
      numberOfShares = numberOfShares,
      closeDate = Option(openDate).map(_.toInstant),
      closePricePerShare = Option(closePricePerShare),
      pnl = Option(pnl)
    )

  override def findOpenBetween(from: Instant, to: Instant): TranzactIO[List[Position]] =
    tzio {
      sql"""SELECT id,
           symbol,
           number_of_shares,
           open_price_per_share,
           close_price_per_share,
           open_date,
           close_date,
           pnl FROM position WHERE close_date is null AND open_date between ${Date.from(from)} AND ${Date.from(to)}"""
        .query[PositionDB]
        .to[List]
        .map(_.map(_.toDomain))
    }

  override def findCloseBetween(from: Instant, to: Instant): TranzactIO[List[Position]] = tzio {
    sql"""SELECT id,
           symbol,
           number_of_shares,
           open_price_per_share,
           close_price_per_share,
           open_date,
           close_date,
           pnl FROM position WHERE close_date is not null AND close_date between ${Date.from(from)} AND ${Date.from(to)}"""
      .query[PositionDB]
      .to[List]
      .map(_.map(_.toDomain))
  }

  override def getPnL(from: Instant, to: Instant): TranzactIO[Double] =
    findCloseBetween(from = from, to = to).map(_.flatMap(_.pnl).sum)
