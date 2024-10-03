package com.jworkdev.trading.robot.service

import com.jworkdev.trading.robot.Order

enum OrderExecutionStatus:
  case Pending, Filled, UnFilled

case class OrderExecutionResult(order: Order, shares: Int, price: Double, status: OrderExecutionStatus)

trait OrderExecutor {
  def execute(orders: List[Order]): List[OrderExecutionResult]
}
