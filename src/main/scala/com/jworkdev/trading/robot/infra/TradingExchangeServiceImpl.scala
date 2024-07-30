package com.jworkdev.trading.robot.infra

import cats.data.NonEmptyList
import com.jworkdev.trading.robot.domain
import com.jworkdev.trading.robot.domain.{TradingExchange, TradingExchangeWindowType}
import com.jworkdev.trading.robot.service.TradingExchangeService
import doobie.*
import doobie.implicits.*
import doobie.implicits.javatimedrivernative.*
import io.github.gaelrenoux.tranzactio.doobie.*
import zio.*
import zio.interop.catz.*

import java.time.LocalTime

class TradingExchangeServiceImpl extends TradingExchangeService:
  private case class TradingExchangeDB(id: String,
                                       name: String,
                                       windowType: String,
                                       openingTime: Option[LocalTime],
                                       closingTime: Option[LocalTime],
                                       timezone: Option[String]){
    def toDomain: TradingExchange =
      TradingExchange(id = id,
        name = name,
        windowType = TradingExchangeWindowType.valueOf(windowType),
        openingTime = openingTime,
        closingTime = closingTime,
        timezone = timezone)
  }

  override def findAll(): TranzactIO[List[TradingExchange]] = tzio {
    sql"""SELECT te.id,
          te.name,
          te.window_type,
          te.openingTime,
          te.closingTime,
          te.timezone FROM trading_exchange te """
      .query[TradingExchangeDB]
      .to[List].map(_.map(_.toDomain))
  }

  override def findById(id: String): TranzactIO[Option[TradingExchange]] = tzio {
    sql"""SELECT
          id,
          name,
          window_type,
          openingTime,
          closingTime,
          timezone FROM exchange WHERE id = $id"""
      .query[TradingExchangeDB]
      .to[List].map(_.headOption.map(_.toDomain))
  }

  override def findBySymbol(symbol: String): TranzactIO[Option[TradingExchange]] = tzio {
    sql"""SELECT te.id,
          te.name,
          te.window_type,
          te.openingTime,
          te.closingTime,
          te.timezone FROM fin_instrument fi
	      inner join trading_exchange te on fi.exchange = td.id
          WHERE symbol = $symbol"""
      .query[TradingExchangeDB]
      .to[List].map(_.headOption.map(_.toDomain))
  }

  override def findBySymbols(symbols: Set[String]): TranzactIO[Set[TradingExchange]] = tzio {
    val list = NonEmptyList.fromList(symbols.toList).get
    val q = fr"""SELECT te.id,
          te.name,
          window_type,
          te.openingTime,
          te.closingTime,
          te.timezone FROM fin_instrument fi
	      inner join trading_exchange te on fi.exchange = td.id
          WHERE """ ++ Fragments.in(fr"symbol", list)
    q.query[TradingExchangeDB].to[Set].map(_.map(_.toDomain))
  }

object TradingExchangeService:
  val layer: ULayer[TradingExchangeService] = ZLayer.succeed(new TradingExchangeServiceImpl)