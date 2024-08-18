package com.jworkdev.trading.robot.service

import com.jworkdev.trading.robot.Order
import com.jworkdev.trading.robot.config.{StrategyConfigurations, TradingMode}
import com.jworkdev.trading.robot.data.signals.SignalFinderStrategy
import com.jworkdev.trading.robot.data.strategy
import com.jworkdev.trading.robot.data.strategy.{
  MarketDataStrategyProvider,
  MarketDataStrategyRequest,
  MarketDataStrategyRequestFactory,
  MarketDataStrategyResponse
}
import com.jworkdev.trading.robot.domain.*
import com.jworkdev.trading.robot.market.data.MarketDataProvider
import com.typesafe.scalalogging.Logger
import zio.{Task, ZIO}

import java.time.{LocalDateTime, ZonedDateTime}
import scala.util.Try

case class TradingExecutorRequest(
    balancePerFinInst: Double,
    finInstruments: List[FinInstrument],
    tradingStrategies: List[TradingStrategy],
    openPositions: List[Position],
    exchangeMap: Map[String, TradingExchange],
    strategyConfigurations: StrategyConfigurations,
    stopLossPercentage: Int,
    tradingMode: TradingMode,
    tradingDateTime: ZonedDateTime
)

trait TradingExecutorService:
  def execute(
      request: TradingExecutorRequest
  ): Task[List[Order]]

class TradingExecutorServiceImpl(
    marketDataProvider: MarketDataProvider,
    marketDataStrategyProvider: MarketDataStrategyProvider[MarketDataStrategyRequest, MarketDataStrategyResponse],
    marketDataStrategyRequestFactory: MarketDataStrategyRequestFactory,
    signalFinderStrategy: SignalFinderStrategy,
    orderFactory: OrderFactory
) extends TradingExecutorService:
  private val logger = Logger(classOf[TradingExecutorServiceImpl])

  override def execute(
      request: TradingExecutorRequest
  ): Task[List[Order]] =
    val input = request.tradingStrategies.flatMap(tradingStrategy =>
      request.finInstruments.map(finInstrument => (tradingStrategy, finInstrument))
    )
    for
      _ <- ZIO.logInfo(
        s"Trading on  ${request.finInstruments.map(_.symbol)} using ${request.tradingStrategies}"
      )
      fibers <- ZIO.foreach(input) { case (tradingStrategy: TradingStrategy, finInstrument: FinInstrument) =>
        execute(
          balancePerFinInst = request.balancePerFinInst,
          finInstrument = finInstrument,
          tradingStrategy = tradingStrategy,
          openPositions = request.openPositions,
          exchangeMap = request.exchangeMap,
          strategyConfigurations = request.strategyConfigurations,
          stopLossPercentage = request.stopLossPercentage,
          tradingMode = request.tradingMode,
          currentLocalTime = request.tradingDateTime
        ).fork
      }
      results <- ZIO.foreach(fibers)(_.join)
    yield results.flatten

  private def execute(
      balancePerFinInst: Double,
      finInstrument: FinInstrument,
      tradingStrategy: TradingStrategy,
      openPositions: List[Position],
      exchangeMap: Map[String, TradingExchange],
      strategyConfigurations: StrategyConfigurations,
      stopLossPercentage: Int,
      tradingMode: TradingMode,
      currentLocalTime: ZonedDateTime
  ): Task[Option[Order]] =
    for
      _ <- ZIO.logInfo(s"Executing ${finInstrument.symbol} ...")
      currentPriceFiber <- ZIO
        .attempt(marketDataProvider.getCurrentMarketPriceQuote(symbol = finInstrument.symbol))
        .fork
      marketDataStrategyResponseFiber <- ZIO
        .attempt(
          marketDataStrategyRequestFactory
            .createMarketDataStrategyRequest(
              symbol = finInstrument.symbol,
              tradingStrategyType = tradingStrategy.`type`,
              strategyConfigurations = strategyConfigurations
            )
            .flatMap(request => marketDataStrategyProvider.provide(request))
        )
        .fork
      marketDataStrategyResponse <- marketDataStrategyResponseFiber.join
      currentPriceRes <- currentPriceFiber.join
      _ <- ZIO.logInfo(s"Data for ${finInstrument.symbol} fetched!")
      orders <- currentPriceRes.fold(
        ex =>
          logger.error("Error fetching current price!", ex)
          ZIO.none
        ,
        currentPrice =>
          execute(
            balancePerFinInst = balancePerFinInst,
            finInstrument = finInstrument,
            tradingStrategy = tradingStrategy,
            openPosition = findOpenPositionForSymbol(
              symbol = finInstrument.symbol,
              tradingStrategy = tradingStrategy,
              openPositions = openPositions
            ),
            exchangeMap = exchangeMap,
            strategyConfigurations = strategyConfigurations,
            tradingMode = tradingMode,
            stopLossPercentage = stopLossPercentage,
            currentPrice = currentPrice,
            currentLocalTime = currentLocalTime,
            marketDataStrategyResponse = marketDataStrategyResponse
          )
      )
    yield orders

  private def findOpenPositionForSymbol(
      symbol: String,
      tradingStrategy: TradingStrategy,
      openPositions: List[Position]
  ): Option[Position] =
    openPositions.find(position =>
      symbol == position.symbol &&
        tradingStrategy.`type` == position.tradingStrategyType
    )

  private def execute(
      balancePerFinInst: Double,
      finInstrument: FinInstrument,
      tradingStrategy: TradingStrategy,
      openPosition: Option[Position],
      exchangeMap: Map[String, TradingExchange],
      strategyConfigurations: StrategyConfigurations,
      tradingMode: TradingMode,
      stopLossPercentage: Int,
      currentPrice: Double,
      currentLocalTime: ZonedDateTime,
      marketDataStrategyResponse: Try[MarketDataStrategyResponse]
  ): Task[Option[Order]] =
    ZIO.attempt(
      orderFactory.create(orderRequest =
        OrderRequest(
          balancePerFinInst = balancePerFinInst,
          finInstrument = finInstrument,
          tradingStrategy = tradingStrategy,
          openPosition = openPosition,
          exchangeMap = exchangeMap,
          tradingMode = tradingMode,
          stopLossPercentage = stopLossPercentage,
          tradingPrice = currentPrice,
          tradeDateTime = currentLocalTime,
          marketDataStrategyResponse = marketDataStrategyResponse
        )
      )
    )

object TradingExecutorService:
  def apply(): TradingExecutorService =
    val signalFinderStrategy = SignalFinderStrategy()
    new TradingExecutorServiceImpl(
      MarketDataProvider(),
      MarketDataStrategyProvider(),
      MarketDataStrategyRequestFactory(),
      signalFinderStrategy,
      OrderFactory(signalFinderStrategy = signalFinderStrategy)
    )
