package com.jworkdev.trading.robot.infra

import com.jworkdev.trading.robot.domain.{FinInstrument, FinInstrumentType}
import com.jworkdev.trading.robot.service.{FinInstrumentService, FinInstrumentStats}
import doobie.*
import doobie.implicits.*
import doobie.implicits.javatimedrivernative.*
import io.github.gaelrenoux.tranzactio.DbException
import io.github.gaelrenoux.tranzactio.doobie.*
import zio.*

import java.time.{Instant, ZonedDateTime}
import scala.util.Try

class FinInstrumentServiceImpl extends FinInstrumentService:

  private case class FinInstrumentDB(
      symbol: String,
      name: String,
      `type`: String,
      priceVolatility: Option[Double],
      averageDailyVolume: Option[Double],
      preMarketGap: Option[Double],
      preMarketNumberOfShareTrades: Option[Double],
      averageTrueRange: Option[Double],
      exchange: String,
      creationDate: ZonedDateTime,
      lastUpdate: Option[ZonedDateTime],
      active: String
  ):
    def toDomain: FinInstrument = FinInstrument(
      symbol = symbol,
      name = name,
      `type` = FinInstrumentType.valueOf(`type`),
      priceVolatility = priceVolatility,
      averageDailyVolume = averageDailyVolume,
      preMarketGap = preMarketGap,
      preMarketNumberOfShareTrades = preMarketNumberOfShareTrades,
      averageTrueRange = averageTrueRange,
      exchange = exchange,
      creationDate = creationDate,
      lastUpdate = lastUpdate,
      isActive = "YES".equalsIgnoreCase(active)
    )

  private object FinInstrumentDB:
    def fromDomain(finInstrument: FinInstrument): FinInstrumentDB = FinInstrumentDB(
      symbol = finInstrument.symbol,
      name = finInstrument.name,
      `type` = finInstrument.`type`.toString,
      priceVolatility = finInstrument.priceVolatility,
      averageDailyVolume = finInstrument.averageDailyVolume,
      preMarketGap = finInstrument.preMarketGap,
      preMarketNumberOfShareTrades = finInstrument.preMarketNumberOfShareTrades,
      averageTrueRange = finInstrument.averageTrueRange,
      exchange = finInstrument.exchange,
      creationDate = finInstrument.creationDate,
      lastUpdate = finInstrument.lastUpdate,
      active =
        if finInstrument.isActive then "YES"
        else "NO"
    )

  override def findBySymbol(symbol: String): ZIO[Transactor[Task], DbException, Option[FinInstrument]] = tzio {
    sql"""SELECT
             symbol,
             name,
             type,
             price_volatility,
             avg_daily_volume,
             pre_market_gap,
             pre_market_trades,
             average_true_range,
             exchange,
             creation_date,
             last_update,
             active
             FROM fin_instrument WHERE symbol=${symbol}"""
      .query[FinInstrumentDB]
      .option
      .map(_.map(_.toDomain))
  }

  override def findAll(): TranzactIO[List[FinInstrument]] = tzio {
    sql"""SELECT
             symbol,
             name,
             type,
             price_volatility,
             avg_daily_volume,
             pre_market_gap,
             pre_market_trades,
             average_true_range,
             exchange,
             creation_date,
             last_update,
             active
             FROM fin_instrument"""
      .query[FinInstrumentDB]
      .to[List]
      .map(_.map(_.toDomain))
  }

  override def findTopVolatile(limit: Int): TranzactIO[List[FinInstrument]] = tzio {
    sql"""SELECT
               symbol,
               name,
               type,
               price_volatility,
               avg_daily_volume,
               pre_market_gap,
               pre_market_trades,
               average_true_range,
               exchange,
               creation_date,
               last_update,
               active
               FROM fin_instrument order by price_volatility desc LIMIT ${limit}"""
      .query[FinInstrumentDB]
      .to[List]
      .map(_.map(_.toDomain))
  }

  override def findTopToTrade(limit: Int): TranzactIO[List[FinInstrument]] = tzio {
    sql"""SELECT
             symbol,
             name,
             type,
             price_volatility,
             avg_daily_volume,
             pre_market_gap,
             pre_market_trades,
             average_true_range,
             exchange,
             creation_date,
             last_update,
             active
             FROM fin_instrument order by price_volatility desc LIMIT ${limit}"""
      .query[FinInstrumentDB]
      .to[List]
      .map(_.map(_.toDomain))
  }

  override def findWithExpiredStats(): ZIO[Transactor[Task], DbException, List[FinInstrument]] = tzio {
    sql"""SELECT
             symbol,
             name,
             type,
             price_volatility,
             avg_daily_volume,
             pre_market_gap,
             pre_market_trades,
             average_true_range,
             exchange,
             creation_date,
             last_update,
             active
             FROM fin_instrument WHERE (price_volatility is null and last_update is null) or (last_update  < date_add(now(), interval -2 week) and active='YES' ) LIMIT 200"""
      .query[FinInstrumentDB]
      .to[List]
      .map(_.map(_.toDomain))
  }

  private def saveAll(finInstruments: List[FinInstrumentDB]): TranzactIO[Int] =
    val sql =
      "INSERT INTO fin_instrument (symbol,name,type,price_volatility,avg_daily_volume,pre_market_gap,pre_market_trades,average_true_range,exchange,creation_date,last_update,active) VALUES (?,?,?,?,?,?,?)"
    val update: Update[FinInstrumentDB] = Update[FinInstrumentDB](sql)
    tzio {
      update.updateMany(finInstruments)
    }.mapError(e => DbException.Wrapped(e))

  override def saveNonExisting(finInstruments: List[FinInstrument]): TranzactIO[Int] =
    val input = finInstruments.map(FinInstrumentDB.fromDomain)
    for
      existing <- ZIO.foreach(input)(entry => findBySymbol(symbol = entry.symbol)).map(_.flatMap(_.map(_.symbol)))
      toCreate <- ZIO.succeed(input.filter(in => !existing.contains(in.symbol)))
      res <- saveAll(finInstruments = toCreate)
    yield res

  override def updateVolatility(volatilityMap: Map[String, Try[Double]]): ZIO[Transactor[Task], DbException, Int] =
    val sqlUpdateSuccess = "UPDATE fin_instrument SET price_volatility = ?, last_update=?  WHERE symbol=?"
    val now = Instant.now()
    val success = volatilityMap.filter(entry => entry._2.isSuccess).map(entry => (entry._1, entry._2.get))
    val inputSuccess = success.map { case (key: String, value: Double) => (value, now, key) }.toList
    val updateSuccess: Update[(Double, Instant, String)] = Update[(Double, Instant, String)](sqlUpdateSuccess)
    val sqlUpdateFailure = "UPDATE fin_instrument SET last_update=?  WHERE symbol=?"
    val failure = volatilityMap.filter(entry => entry._2.isFailure).keys
    val inputFailure = failure.map(symbol => (now, symbol)).toList
    val updateFailure: Update[(Instant, String)] = Update[(Instant, String)](sqlUpdateFailure)
    for
      countSuccess <- tzio {
        updateSuccess.updateMany(inputSuccess)
      }.mapError(e => DbException.Wrapped(e))
      countFailure <- tzio {
        updateFailure.updateMany(inputFailure)
      }.mapError(e => DbException.Wrapped(e))
    yield countSuccess + countFailure

  override def updateStats(stats: List[FinInstrumentStats]): ZIO[Transactor[Task], DbException, Int] =
    val sqlUpdate =
      "UPDATE fin_instrument SET avg_daily_volume = ?, pre_market_gap = ?, pre_market_trades = ?, average_true_range = ?, last_update=?  WHERE symbol=?"
    val update: Update[(Double, Option[Double], Option[Double], Option[Double], Instant, String)] =
      Update[(Double, Option[Double], Option[Double], Option[Double], Instant, String)](sqlUpdate)
    val now = Instant.now()
    val input = stats.map { statsEntry =>
      (
        statsEntry.averageDailyVolume,
        statsEntry.preMarketGap,
        statsEntry.preMarketNumberOfShareTrades,
        statsEntry.averageTrueRange,
        now,
        statsEntry.symbol
      )
    }
    for
      count <- tzio {
        update.updateMany(input)
      }.mapError(e => DbException.Wrapped(e))
    yield count

  override def updateVolatility(symbol: String, volatility: Double): ZIO[Transactor[Task], DbException, Unit] = tzio {
    sql"""UPDATE fin_instrument SET price_volatility = ${volatility}, last_update=${Instant
      .now()}  WHERE symbol=${symbol}""".update.run
      .map(_ => ())
  }

  override def deleteAll(): TranzactIO[Int] = tzio {
    sql"""
        delete from fin_instrument
      """.update.run
  }

object FinInstrumentService:
  val layer: ULayer[FinInstrumentService] = ZLayer.succeed(new FinInstrumentServiceImpl)
