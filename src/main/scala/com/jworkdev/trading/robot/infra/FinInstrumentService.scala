package com.jworkdev.trading.robot.infra

import com.jworkdev.trading.robot.domain.{FinInstrument, FinInstrumentType}
import com.jworkdev.trading.robot.service.FinInstrumentService
import doobie.*
import doobie.implicits.*
import doobie.implicits.legacy.instant.*
import io.github.gaelrenoux.tranzactio.DbException
import io.github.gaelrenoux.tranzactio.doobie.*
import zio.*

import java.time.Instant

class FinInstrumentServiceImpl extends FinInstrumentService:

  private case class FinInstrumentDB(
      symbol: String,
      `type`: String,
      volatility: Double,
      exchange: String,
      creationDate: Instant
  ):
    def toDomain: FinInstrument = FinInstrument(
      symbol = symbol,
      `type` = FinInstrumentType.valueOf(`type`),
      volatility = volatility,
      exchange = exchange,
      creationDate = creationDate
    )

  private object FinInstrumentDB{
    def fromDomain(finInstrument: FinInstrument): FinInstrumentDB = FinInstrumentDB(
      symbol = finInstrument.symbol,
      `type` = finInstrument.`type`.toString,
      volatility = finInstrument.volatility,
      exchange = finInstrument.exchange,
      creationDate = finInstrument.creationDate
    )
  }

  override def findAll(): TranzactIO[List[FinInstrument]] = tzio {
    sql"""SELECT
             symbol,
             type,
             volatility,
             exchange,
             creation_date
             FROM fin_instrument"""
      .query[FinInstrumentDB]
      .to[List]
      .map(_.map(_.toDomain))
  }

  override def saveAll(finInstruments: List[FinInstrument]): TranzactIO[Int] = {
    val input = finInstruments.map(FinInstrumentDB.fromDomain)
    val sql = "INSERT INTO fin_instrument (symbol,type,volatility,exchange,creation_date) VALUES (?,?,?,?,?)"
    val update: Update[FinInstrumentDB] = Update[FinInstrumentDB](sql)
    tzio {
      update.updateMany(input)
    }.mapError(e => DbException.Wrapped(e))
  }

  override def deleteAll(): TranzactIO[Int] = tzio{
    sql"""
        delete from fin_instrument
      """.update.run
  }
  
object FinInstrumentService:
  val layer: ULayer[FinInstrumentService] = ZLayer.succeed(new FinInstrumentServiceImpl)