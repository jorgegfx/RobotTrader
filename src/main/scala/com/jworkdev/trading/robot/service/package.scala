package com.jworkdev.trading.robot

import com.jworkdev.trading.robot.domain.{Account, FinInstrumentConfig, Position}
import doobie.implicits.*
import io.github.gaelrenoux.tranzactio.doobie
import io.github.gaelrenoux.tranzactio.doobie.{TranzactIO, tzio}
import zio.{ULayer, ZLayer}

import java.time.Instant


package object service {
  trait AccountService:
    def findByName(name: String): TranzactIO[Account]
    def updateBalance(id: Long, newBalance: Double): TranzactIO[Unit]

  trait FinInstrumentConfigService:
    def findAll(): TranzactIO[List[FinInstrumentConfig]]
    def updatePnl(symbol: String, currentPnl: Double): TranzactIO[Unit]

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
