package com.jworkdev.trading.robot.infra

import com.jworkdev.trading.robot.domain.{FinInstrument, FinInstrumentType, TradingStrategyType}
import com.jworkdev.trading.robot.service.FinInstrumentService
import doobie.implicits.*
import doobie.implicits.legacy.instant.*
import io.github.gaelrenoux.tranzactio.{DbException, doobie}
import io.github.gaelrenoux.tranzactio.doobie.{TranzactIO, tzio}
import zio.{Task, ULayer, ZIO, ZLayer}

import java.time.Instant

class FinInstrumentServiceImpl extends FinInstrumentService:

  private case class FinInstrumentDB(
      symbol: String,
      finInstrumentType: String
  ):
    def toDomain: FinInstrument = FinInstrument(
      symbol = symbol,
      finInstrumentType = FinInstrumentType.valueOf(finInstrumentType),
    )

  override def findAll(): TranzactIO[List[FinInstrument]] = tzio {
    sql"""SELECT
             symbol,
             finInstrumentType
             FROM fin_instr_config"""
      .query[FinInstrumentDB]
      .to[List]
      .map(_.map(_.toDomain))
  }

  override def saveAll(finInstruments: List[FinInstrument]): TranzactIO[Unit] = ???

  override def deleteAll(): TranzactIO[Unit] = ???
  
object FinInstrumentService:
  val layer: ULayer[FinInstrumentService] = ZLayer.succeed(new FinInstrumentServiceImpl)