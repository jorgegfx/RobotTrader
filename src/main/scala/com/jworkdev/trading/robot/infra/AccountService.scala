package com.jworkdev.trading.robot.infra

import com.jworkdev.trading.robot.domain.Account
import doobie.implicits.*
import io.github.gaelrenoux.tranzactio.doobie
import io.github.gaelrenoux.tranzactio.doobie.{TranzactIO, tzio}
import zio.{ULayer, ZLayer}

trait AccountService:
  def findByName(name: String): TranzactIO[Account]
  def updateBalance(id: Long, newBalance: Double): TranzactIO[Unit]

class AccountServiceImpl extends AccountService:
  override def findByName(name: String): TranzactIO[Account] = tzio {
    sql"""SELECT id,
             name,
             balance FROM account"""
      .query[Account]
      .to[List].map(_.head)
  }

  override def updateBalance(id: Long, newBalance: Double): TranzactIO[Unit] = tzio {
    sql"""UPDATE account WHERE
         id= $id, balance = $newBalance""".update.run
      .map(_ => ())
  }

object AccountService:
  val layer: ULayer[AccountService] = ZLayer.succeed(new AccountServiceImpl)
