package com.jworkdev.trading.robot.infra

import com.jworkdev.trading.robot.domain.Position
import doobie.implicits.*
import doobie.implicits.legacy.instant._
import io.github.gaelrenoux.tranzactio.doobie
import io.github.gaelrenoux.tranzactio.doobie.{TranzactIO, tzio}
import zio.{ULayer, ZLayer}

import java.time.{Instant, LocalDate, LocalDateTime, ZoneId}
import java.util.Date


trait PositionService:
  def create(position: Position): TranzactIO[Unit]

  def findAll(): TranzactIO[List[Position]]

  def findOpenBetween(from: Instant, to: Instant): TranzactIO[List[Position]]

  def findCloseBetween(from: Instant, to: Instant): TranzactIO[List[Position]]

  def getPnL(from: Instant, to: Instant): TranzactIO[Double]

class PositionServiceImpl extends PositionService:

  override def create(position: Position): TranzactIO[Unit] = tzio {
    sql"""INSERT INTO position (symbol,number_of_shares,open_date,open_price_per_share)
         VALUES (${position.symbol},
         ${position.numberOfShares},
         ${position.openDate},
         ${position.openPricePerShare})""".update.run
      .map(_ => ())
  }

  override def findAll(): TranzactIO[List[Position]] =
    tzio {
      sql"""SELECT id,
             symbol,
             number_of_shares,
             open_price_per_share,
             close_price_per_share,
             open_date,
             close_date,
             pnl FROM position"""
        .query[Position]
        .to[List]
    }

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
        .query[Position]
        .to[List]
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
      .query[Position]
      .to[List]
  }

  override def getPnL(from: Instant, to: Instant): TranzactIO[Double] =
    findCloseBetween(from = from, to = to).map(_.flatMap(_.pnl).sum)


object PositionServiceLayer:
  val layer: ULayer[PositionService] = ZLayer.succeed(new PositionServiceImpl)
