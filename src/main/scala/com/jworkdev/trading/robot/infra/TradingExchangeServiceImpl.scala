package com.jworkdev.trading.robot.infra

import cats.data.NonEmptyList
import com.jworkdev.trading.robot.domain
import com.jworkdev.trading.robot.domain.TradingExchange
import com.jworkdev.trading.robot.service.TradingExchangeService
import doobie.implicits.javatimedrivernative.*
import io.github.gaelrenoux.tranzactio.DbException
import io.github.gaelrenoux.tranzactio.doobie.*
import zio.*
import cats.implicits.*
import doobie.*
import doobie.implicits.*
import zio.interop.catz.*
import zio.interop.catz.implicits.*

import java.time.LocalTime

class TradingExchangeServiceImpl extends TradingExchangeService:
  override def findAll(): TranzactIO[List[TradingExchange]] = tzio {
    sql"""SELECT te.id,
          te.name,
          te.openingTime,
          te.closingTime,
          te.timezone FROM trading_exchange te """
      .query[TradingExchange]
      .to[List]
  }

  override def findById(id: String): TranzactIO[Option[TradingExchange]] = tzio {
    sql"""SELECT
          id,
          name,
          openingTime,
          closingTime,
          timezone FROM exchange WHERE id = $id"""
      .query[TradingExchange]
      .to[List].map(_.headOption)
  }

  override def findBySymbol(symbol: String): TranzactIO[Option[TradingExchange]] = tzio {
    sql"""SELECT te.id,
          te.name,
          te.openingTime,
          te.closingTime,
          te.timezone FROM fin_instrument fi
	      inner join trading_exchange te on fi.exchange = td.id
          WHERE symbol = $symbol"""
      .query[TradingExchange]
      .to[List].map(_.headOption)
  }

  override def findBySymbols(symbols: Set[String]): TranzactIO[Set[TradingExchange]] = tzio {
    val list = NonEmptyList.fromList(symbols.toList).get
    val q = fr"""SELECT te.id,
          te.name,
          te.openingTime,
          te.closingTime,
          te.timezone FROM fin_instrument fi
	      inner join trading_exchange te on fi.exchange = td.id
          WHERE """ ++ Fragments.in(fr"symbol", list)
    q.query[TradingExchange].to[Set]
  }

object TradingExchangeService:
  val layer: ULayer[TradingExchangeService] = ZLayer.succeed(new TradingExchangeServiceImpl)