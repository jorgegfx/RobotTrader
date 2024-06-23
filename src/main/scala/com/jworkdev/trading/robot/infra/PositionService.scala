package com.jworkdev.trading.robot.infra

trait PositionService:
  def create(position: Position): Unit
  def findBetween(from: Instant, to: Instant):List[Position]
  def getPnL(from: Instant, to: Instant): Double
