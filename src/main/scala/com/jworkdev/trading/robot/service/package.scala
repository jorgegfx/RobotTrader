package com.jworkdev.trading.robot

import com.jworkdev.trading.robot.domain.{Account, FinInstrument, PnLPerformance, Position, TradingExchange, TradingStrategy, TradingStrategyType}
import doobie.implicits.*
import io.github.gaelrenoux.tranzactio.doobie
import io.github.gaelrenoux.tranzactio.doobie.{TranzactIO, tzio}
import zio.{ULayer, ZLayer}

import java.time.{Instant, LocalDate}
import scala.util.Try


package object service {
  trait AccountService:
    def findByName(name: String): TranzactIO[Account]
    def updateBalance(id: Long, newBalance: Double): TranzactIO[Unit]

  trait TradingStrategyService:
    def findAll(): TranzactIO[List[TradingStrategy]]
    def updatePnl(`type`: TradingStrategyType, currentPnl: Double): TranzactIO[Unit]

  trait FinInstrumentService:
    def findBySymbol(symbol: String): TranzactIO[Option[FinInstrument]]
    def findAll(): TranzactIO[List[FinInstrument]]
    def findTopVolatile(limit: Int): TranzactIO[List[FinInstrument]]
    def findTopToTrade(limit: Int): TranzactIO[List[FinInstrument]]
    def findWithExpiredStats(): TranzactIO[List[FinInstrument]]
    def saveNonExisting(finInstruments: List[FinInstrument]): TranzactIO[Int]
    def updateVolatility(volatilityMap: Map[String,Try[Double]]): TranzactIO[Int]
    def updateStats(stats: List[FinInstrumentStats]): TranzactIO[Int]
    def updateVolatility(symbol: String, volatility: Double): TranzactIO[Unit]
    def deleteAll(): TranzactIO[Int]

  trait TradingExchangeService:
    def findAll(): TranzactIO[List[TradingExchange]]
    def findById(id: String): TranzactIO[Option[TradingExchange]]
    def findBySymbol(symbol: String): TranzactIO[Option[TradingExchange]]
    def findBySymbols(symbols: Set[String]): TranzactIO[Set[TradingExchange]]

  trait PositionService:
    def create(position: Position): TranzactIO[Unit]
    def findAll(): TranzactIO[List[Position]]
    def findAllOpen(): TranzactIO[List[Position]]
    def closeOpenPositions(openPositions: List[Position], orders: List[Order]): TranzactIO[Int]
    def createOpenPositionsFromOrders(orders: List[Order]): TranzactIO[Int]
    def findOpenBetween(from: Instant, to: Instant): TranzactIO[List[Position]]
    def findCloseBetween(from: Instant, to: Instant): TranzactIO[List[Position]]
    def getPnL(from: Instant, to: Instant): TranzactIO[Double]

  trait OrderService:
    def findAll(): TranzactIO[List[Order]]
    def create(orders: List[Order]): TranzactIO[Int]

  trait PnLPerformanceService:
    def findByEntryDate(entryDate: LocalDate,
                        tradingStrategyType: TradingStrategyType): TranzactIO[Option[PnLPerformance]]
    def createOrUpdate(entryDate: LocalDate,
                       tradingStrategyType: TradingStrategyType,
                       amount: Double): TranzactIO[Unit]
}
