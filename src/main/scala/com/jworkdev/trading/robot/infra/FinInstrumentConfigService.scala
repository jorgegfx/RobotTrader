package com.jworkdev.trading.robot.infra

import com.jworkdev.trading.robot.domain.{FinInstrumentConfig, FinInstrumentType, TradingStrategyType}
import com.jworkdev.trading.robot.service.FinInstrumentConfigService
import doobie.implicits.*
import doobie.implicits.legacy.instant.*
import io.github.gaelrenoux.tranzactio.doobie
import io.github.gaelrenoux.tranzactio.doobie.{TranzactIO, tzio}
import zio.{ULayer, ZLayer}

import java.time.Instant

class FinInstrumentConfigServiceImpl extends FinInstrumentConfigService:

  private case class FinInstrumentConfigDB(
      symbol: String,
      pnl: Option[Double],
      finInstrumentType: String,
      strategy: String,
      lastPnlUpdate: Option[Instant]
  ):
    def toDomain: FinInstrumentConfig = FinInstrumentConfig(
      symbol = symbol,
      pnl = pnl,
      strategy = TradingStrategyType.valueOf(strategy),
      finInstrumentType = FinInstrumentType.valueOf(finInstrumentType),
      lastPnlUpdate = lastPnlUpdate
    )

  def findAll(): TranzactIO[List[FinInstrumentConfig]] = tzio {
    sql"""SELECT
             symbol,
             pnl,
             type,
             strategy,
             last_pnl_update FROM fin_instr_config"""
      .query[FinInstrumentConfigDB]
      .to[List]
      .map(_.map(_.toDomain))
  }

  def updatePnl(symbol: String, currentPnl: Double): TranzactIO[Unit] = tzio {
    sql"""UPDATE fin_instr_config WHERE
         symbol= $symbol, pnl = $currentPnl""".update.run
      .map(_ => ())
  }
object FinInstrumentConfigService:
  val layer: ULayer[FinInstrumentConfigService] = ZLayer.succeed(new FinInstrumentConfigServiceImpl)