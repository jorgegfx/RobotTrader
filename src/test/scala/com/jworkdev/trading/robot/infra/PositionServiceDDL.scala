package com.jworkdev.trading.robot.infra

import doobie.implicits.*
import io.github.gaelrenoux.tranzactio.doobie
import io.github.gaelrenoux.tranzactio.doobie.{TranzactIO, tzio}

trait PositionServiceDDL:
  def initialize(): TranzactIO[Unit]


class PositionServiceDDLImpl extends PositionServiceDDL:
  override def initialize(): TranzactIO[Unit] = tzio {
    sql"""
        CREATE TABLE IF NOT EXISTS position (
        id BIGINT AUTO_INCREMENT PRIMARY KEY,
        symbol VARCHAR(10) NOT NULL,
        number_of_shares INT NOT NULL,
        open_price_per_share DOUBLE NOT NULL,
        close_price_per_share DOUBLE,
        open_date TIMESTAMP NOT NULL,
        close_date TIMESTAMP,
        pnl DOUBLE,
        trading_strategy_type VARCHAR(10) NOT NULL
      )
      """.update.run.map(_ => ())
  }
