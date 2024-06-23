package com.jworkdev.trading.robot.infra

import doobie._
import doobie.hikari.HikariTransactor
import doobie.implicits._
import zio._
import zio.interop.catz._
import zio.interop.catz.implicits._

object Database:
  val transactorLayer: TaskLayer[Transactor[Task]] = {
    val transactor = for {
      ce <- ZIO.runtime[Any]
      transactor <- HikariTransactor.newHikariTransactor[Task](
        driverClassName = "com.mysql.cj.jdbc.Driver",
        url = "jdbc:mysql://localhost:3306/zio_crud",
        user = "username",
        pass = "password",
        connectEC = ce.platform.executor.asEC,
        blocker = Blocker.liftExecutionContext(ce.platform.executor.asEC)
      )
    } yield transactor

    transactor.toLayer
  }

