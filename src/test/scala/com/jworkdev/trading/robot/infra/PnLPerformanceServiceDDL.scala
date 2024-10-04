package com.jworkdev.trading.robot.infra

import doobie.implicits.*
import io.github.gaelrenoux.tranzactio.doobie
import io.github.gaelrenoux.tranzactio.doobie.{TranzactIO, tzio}

trait PnLPerformanceServiceDDL:
  def initialize(): TranzactIO[Unit]

class PnLPerformanceServiceDDLImpl extends PnLPerformanceServiceDDL:
  def initialize(): TranzactIO[Unit] = tzio {
    sql"""
        CREATE TABLE IF NOT EXISTS pnl_performance (
          entry_date DATE NOT NULL,
          trading_strategy_type VARCHAR(20) NOT NULL,
          amount DOUBLE NOT NULL,
          PRIMARY KEY (entry_date, trading_strategy_type)
        )
      """.update.run.map(_ => ())
  }
