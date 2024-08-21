package com.jworkdev.trading.robot.infra

import io.github.gaelrenoux.tranzactio.doobie.TranzactIO

trait PositionServiceDDL {
  def initialize(): TranzactIO[Unit]
}
