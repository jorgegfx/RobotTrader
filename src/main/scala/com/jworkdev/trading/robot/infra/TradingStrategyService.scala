package com.jworkdev.trading.robot.infra

import com.jworkdev.trading.robot.domain.{TradingStrategy, TradingStrategyType}
import com.jworkdev.trading.robot.service.TradingStrategyService
import doobie.*
import doobie.implicits.*
import io.github.gaelrenoux.tranzactio.doobie.*
import zio.*

class TradingStrategyServiceImpl extends TradingStrategyService:
  private case class TradingStrategyDB(`type`: String,
                                       pnl: Option[Double]):
    def toDomain: TradingStrategy = TradingStrategy(`type`= TradingStrategyType.valueOf(`type`),pnl = pnl)
  def findAll(): TranzactIO[List[TradingStrategy]] = tzio{
    sql"""SELECT type,
                 pnl FROM trading_strategy"""
      .query[TradingStrategyDB]
      .to[List].map(_.map(_.toDomain))
  }
  def updatePnl(`type`: TradingStrategyType, currentPnl: Double): TranzactIO[Unit] = tzio {
    sql"""UPDATE trading_strategy SET pnl = $currentPnl WHERE type=${`type`.toString}""".update.run
      .map(_ => ())
  }

object TradingStrategyService:
  val layer: ULayer[TradingStrategyService] = ZLayer.succeed(new TradingStrategyServiceImpl)
