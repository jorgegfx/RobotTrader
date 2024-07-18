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
import scala.util.Try

class FinInstrumentServiceImpl extends FinInstrumentService:

  private case class FinInstrumentDB(
      symbol: String,
      `type`: String,
      volatility: Option[Double],
      exchange: String,
      creationDate: Instant,
      lastUpdate: Option[Instant]
  ):
    def toDomain: FinInstrument = FinInstrument(
      symbol = symbol,
      `type` = FinInstrumentType.valueOf(`type`),
      volatility = volatility,
      exchange = exchange,
      creationDate = creationDate,
      lastUpdate = lastUpdate
    )

  private object FinInstrumentDB{
    def fromDomain(finInstrument: FinInstrument): FinInstrumentDB = FinInstrumentDB(
      symbol = finInstrument.symbol,
      `type` = finInstrument.`type`.toString,
      volatility = finInstrument.volatility,
      exchange = finInstrument.exchange,
      creationDate = finInstrument.creationDate,
      lastUpdate = finInstrument.lastUpdate
    )
  }

  override def findBySymbol(symbol: String): ZIO[Transactor[Task], DbException, Option[FinInstrument]] = tzio {
    sql"""SELECT
             symbol,
             type,
             volatility,
             exchange,
             creation_date,
             last_update
             FROM fin_instrument WHERE symbol=${symbol}"""
      .query[FinInstrumentDB].option.map(_.map(_.toDomain))
  }

  override def findAll(): TranzactIO[List[FinInstrument]] = tzio {
    sql"""SELECT
             symbol,
             type,
             volatility,
             exchange,
             creation_date,
             last_update
             FROM fin_instrument"""
      .query[FinInstrumentDB]
      .to[List]
      .map(_.map(_.toDomain))
  }

  override def findWithoutVolatility(): ZIO[Transactor[Task], DbException, List[FinInstrument]] = tzio {
    sql"""SELECT
             symbol,
             type,
             volatility,
             exchange,
             creation_date,
             last_update
             FROM fin_instrument WHERE volatility is null and last_update is null LIMIT 200"""
      .query[FinInstrumentDB]
      .to[List]
      .map(_.map(_.toDomain))
  }

  private def saveAll(finInstruments: List[FinInstrumentDB]): TranzactIO[Int] = {
    val sql = "INSERT INTO fin_instrument (symbol,type,volatility,exchange,creation_date,last_update) VALUES (?,?,?,?,?,?)"
    val update: Update[FinInstrumentDB] = Update[FinInstrumentDB](sql)
    tzio {
      update.updateMany(finInstruments)
    }.mapError(e => DbException.Wrapped(e))
  }

  override def saveNonExisting(finInstruments: List[FinInstrument]): TranzactIO[Int] = {
    val input = finInstruments.map(FinInstrumentDB.fromDomain)
    for{
      existing <- ZIO.foreach(input){entry => findBySymbol(symbol = entry.symbol)}.map(_.flatMap(_.map(_.symbol)))
      toCreate <- ZIO.succeed(input.filter(in => !existing.contains(in.symbol)))
      res <- saveAll(finInstruments = toCreate)
    } yield res
  }

  override def updateVolatility(volatilityMap: Map[String, Try[Double]]): ZIO[Transactor[Task], DbException, Int] = {
    val sqlUpdateSuccess = "UPDATE fin_instrument SET volatility = ?, last_update=?  WHERE symbol=?"
    val now = Instant.now()
    val success = volatilityMap.filter(entry=>entry._2.isSuccess).map(entry=>(entry._1,entry._2.get))
    val inputSuccess = success.
      map{case (key: String, value: Double)=> (value,now,key)}.toList
    val updateSuccess: Update[(Double,Instant,String)] = Update[(Double,Instant,String)](sqlUpdateSuccess)
    val sqlUpdateFailure = "UPDATE fin_instrument SET last_update=?  WHERE symbol=?"
    val failure = volatilityMap.filter(entry => entry._2.isFailure).keys
    val inputFailure = failure.
      map{  symbol => ( now, symbol) }.toList
    val updateFailure: Update[(Instant, String)] = Update[(Instant, String)](sqlUpdateFailure)
    for
      countSuccess <- tzio {
        updateSuccess.updateMany(inputSuccess)
      }.mapError(e => DbException.Wrapped(e))
      countFailure <- tzio {
        updateFailure.updateMany(inputFailure)
      }.mapError(e => DbException.Wrapped(e))
    yield countSuccess + countFailure
  }
  
  override def updateVolatility(symbol: String, volatility: Double): ZIO[Transactor[Task], DbException, Unit] = tzio {
    sql"""UPDATE fin_instrument SET volatility = ${volatility}, last_update=${Instant.now()}  WHERE symbol=${symbol}""".update.run
      .map(_ => ())
  }

  override def deleteAll(): TranzactIO[Int] = tzio{
    sql"""
        delete from fin_instrument
      """.update.run
  }
  
object FinInstrumentService:
  val layer: ULayer[FinInstrumentService] = ZLayer.succeed(new FinInstrumentServiceImpl)