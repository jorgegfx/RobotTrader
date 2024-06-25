package com.jworkdev.trading.robot.infra

import com.jworkdev.trading.robot.domain.{FinInstrumentConfig, Position}
import zio.{Task, ZIO}

trait TradingService:
  def execute(
      finInstrumentConfigs: List[FinInstrumentConfig],
      currentPositions: List[Position]
  ): Task[List[Position]]

class TradingServiceImpl extends TradingService:
  def execute(
      finInstrumentConfigs: List[FinInstrumentConfig],
      currentPositions: List[Position]
  ): Task[List[Position]] = ???
