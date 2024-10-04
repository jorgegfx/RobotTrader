package com.jworkdev.trading.robot.infra

import com.jworkdev.trading.robot.domain
import com.jworkdev.trading.robot.domain.{PnLPerformance, TradingStrategyType}
import com.jworkdev.trading.robot.service.PnLPerformanceService
import doobie.*
import doobie.implicits.*
import doobie.implicits.javatimedrivernative.*
import io.github.gaelrenoux.tranzactio.doobie.*
import zio.*

import java.time.LocalDate

class PnLPerformanceServiceImpl extends PnLPerformanceService:

  private case class PnLPerformanceDB(entryDate: LocalDate, tradingStrategyType: String, amount: Double):
    def toDomain: PnLPerformance = PnLPerformance(
      entryDate = entryDate,
      tradingStrategyType = TradingStrategyType.valueOf(tradingStrategyType),
      amount = amount
    )

  override def findByEntryDate(
      entryDate: LocalDate,
      tradingStrategyType: TradingStrategyType
  ): TranzactIO[Option[PnLPerformance]] = tzio {
    sql"""
         SELECT entry_date,
          trading_strategy_type,
          amount
        FROM pnl_performance WHERE entry_date = $entryDate AND
         trading_strategy_type=${tradingStrategyType.toString}
       """
      .query[PnLPerformanceDB]
      .to[List]
      .map(_.map(_.toDomain).headOption)
  }

  private def create(pnLPerformance: PnLPerformance): TranzactIO[Unit] = tzio {
    sql"""INSERT INTO pnl_performance (entry_date,trading_strategy_type,amount)
          VALUES (${pnLPerformance.entryDate},
          ${pnLPerformance.tradingStrategyType.toString},
          ${pnLPerformance.amount})""".update.run
      .map( _ => ())
  }

  private def update(pnLPerformance: PnLPerformance): TranzactIO[Unit] = tzio {
    sql"""UPDATE pnl_performance SET amount = ${pnLPerformance.amount} WHERE entry_date=${pnLPerformance.entryDate}
         AND trading_strategy_type=${pnLPerformance.tradingStrategyType.toString}""".update.run
      .map(_ => ())
  }

  override def createOrUpdate(
      entryDate: LocalDate,
      tradingStrategyType: TradingStrategyType,
      amount: Double
  ): TranzactIO[Unit] =
    for
      entry <- findByEntryDate(entryDate = entryDate, tradingStrategyType = tradingStrategyType)
      _ <- entry match
        case Some(value) => update(pnLPerformance = value.copy(amount = amount))
        case None => create(pnLPerformance =
          PnLPerformance(entryDate = entryDate, tradingStrategyType = tradingStrategyType, amount = amount)
        )
    yield()


object PnLPerformanceService:
  val layer: ULayer[PnLPerformanceService] = ZLayer.succeed(new PnLPerformanceServiceImpl) 