package com.jworkdev.trading.robot.infra

import com.jworkdev.trading.robot.domain.Account
import com.jworkdev.trading.robot.service.AccountService
import doobie.implicits.*
import io.github.gaelrenoux.tranzactio.doobie
import io.github.gaelrenoux.tranzactio.doobie.{TranzactIO, tzio}
import zio.{ULayer, ZLayer}


class AccountServiceImpl extends AccountService:
  override def findByName(name: String): TranzactIO[Account] = tzio {
    sql"""SELECT id,
             name,
             balance FROM account"""
      .query[Account]
      .to[List].map(_.head)
  }

  override def updateBalance(id: Long, newBalance: Double): TranzactIO[Unit] = tzio {
    sql"""UPDATE account SET balance = $newBalance WHERE id=$id""".update.run
      .map(_ => ())
  }

object AccountService:
  val layer: ULayer[AccountService] = ZLayer.succeed(new AccountServiceImpl)
