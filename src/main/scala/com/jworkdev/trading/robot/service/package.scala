package com.jworkdev.trading.robot

import com.jworkdev.trading.robot.domain.{Account, FinInstrument, Position, TradingStrategy, TradingStrategyType}
import doobie.implicits.*
import io.github.gaelrenoux.tranzactio.doobie
import io.github.gaelrenoux.tranzactio.doobie.{TranzactIO, tzio}
import zio.{ULayer, ZLayer}

import java.time.Instant
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
    def findTopToTrade(): TranzactIO[List[FinInstrument]]
    def findWithoutVolatility(): TranzactIO[List[FinInstrument]]
    def saveNonExisting(finInstruments: List[FinInstrument]): TranzactIO[Int]
    def updateVolatility(volatilityMap: Map[String,Try[Double]]): TranzactIO[Int]
    def updateVolatility(symbol: String, volatility: Double): TranzactIO[Unit]
    def deleteAll(): TranzactIO[Int]


  trait PositionService:
    def create(position: Position): TranzactIO[Unit]
    def findAll(): TranzactIO[List[Position]]
    def findAllOpen(): TranzactIO[List[Position]]
    def closeOpenPositions(openPositions: List[Position], orders: List[Order]): TranzactIO[Int]
    def createOpenPositionsFromOrders(orders: List[Order]): TranzactIO[Int]
    def findOpenBetween(from: Instant, to: Instant): TranzactIO[List[Position]]
    def findCloseBetween(from: Instant, to: Instant): TranzactIO[List[Position]]
    def getPnL(from: Instant, to: Instant): TranzactIO[Double]
  
}
