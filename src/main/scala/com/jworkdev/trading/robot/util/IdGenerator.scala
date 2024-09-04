package com.jworkdev.trading.robot.util

import java.util.UUID

object IdGenerator {
  def generate: String = UUID.randomUUID().toString
}
