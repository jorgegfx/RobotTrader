package com.jworkdev.trading.robot.infra

import com.jworkdev.trading.robot.Order
import com.jworkdev.trading.robot.data.FinancialIInstrumentDataProvider
import com.jworkdev.trading.robot.data.StockQuoteInterval.FiveMinutes
import com.jworkdev.trading.robot.domain.{FinInstrumentConfig, Position}
import zio.{Task, ZIO}

trait TradingExecutorService:
  def execute(
      finInstrumentConfigs: List[FinInstrumentConfig],
      currentPositions: List[Position]
  ): Task[List[Order]]

class TradingExecutorServiceImpl(financialIInstrumentDataProvider: FinancialIInstrumentDataProvider) extends TradingExecutorService:

  private def execute(
      finInstrumentConfig: FinInstrumentConfig,
      currentOpenPositions: List[Position]
  ): List[Order] = {
    val symbolOpenPositions = currentOpenPositions.
        filter(position=>finInstrumentConfig.symbol==position.symbol)
    val signals = financialIInstrumentDataProvider.
      getIntradayQuotes(symbol = finInstrumentConfig.symbol, FiveMinutes)

    List.empty
  }

  def execute(
      finInstrumentConfigs: List[FinInstrumentConfig],
      currentOpenPositions: List[Position]
  ): Task[List[Order]] = for
    fibers <- ZIO.foreach(finInstrumentConfigs)(n =>
      execute(
        finInstrumentConfigs = finInstrumentConfigs,
        currentOpenPositions = currentOpenPositions
      ).fork
    )
    results <- ZIO.foreach(fibers)(_.join)
  yield (results.flatten)
