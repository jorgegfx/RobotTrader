package com.jworkdev.trading.robot.service

import com.jworkdev.trading.robot.Order
import com.jworkdev.trading.robot.OrderType.{Buy, Sell}

trait OrderBalancer :
  def balance(amount: Double, orders: List[Order]): List[Order]


class OrderBalancerImpl extends OrderBalancer:
  def balance(amount: Double, orders: List[Order]): List[Order] =
    val buyOrderCount = orders.count { order => order.`type` == Buy }
    val newBalancePerOrder = amount/buyOrderCount
    orders.map{order =>
      if(order.`type` == Sell){
        order
      }else{
        val sharesToBuy = (newBalancePerOrder/order.price).toLong
        order.copy(shares = sharesToBuy)
      }
    }

object OrderBalancer:
  def apply(): OrderBalancer = new OrderBalancerImpl()