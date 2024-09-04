package com.jworkdev.trading.robot.infra

import doobie.implicits.*
import io.github.gaelrenoux.tranzactio.doobie
import io.github.gaelrenoux.tranzactio.doobie.{TranzactIO, tzio}

trait OrderServiceDDL:
  def initialize(): TranzactIO[Unit]

class OrderServiceDDLImpl extends OrderServiceDDL:
  override def initialize(): TranzactIO[Unit] = tzio {
    sql"""
        CREATE TABLE IF NOT EXISTS trade_order (
          id VARCHAR(40) PRIMARY KEY,
          type VARCHAR(5) NOT NULL,
          symbol VARCHAR(20) NOT NULL,
          date_time TIMESTAMP NOT NULL,
          shares INT NOT NULL,
          price DOUBLE NOT NULL,
          trading_strategy_type VARCHAR(45) NOT NULL,
          position_id BIGINT,
          order_trigger VARCHAR(45) NOT NULL
        )
      """.update.run.map(_ => ())
  }