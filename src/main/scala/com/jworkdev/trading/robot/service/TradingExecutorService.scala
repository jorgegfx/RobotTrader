package com.jworkdev.trading.robot.service

import com.jworkdev.trading.robot.Order
import com.jworkdev.trading.robot.config.{StrategyConfigurations, TradingMode}
import com.jworkdev.trading.robot.data.signals.SignalFinderStrategy
import com.jworkdev.trading.robot.data.strategy
import com.jworkdev.trading.robot.data.strategy.{MarketDataStrategyProvider, MarketDataStrategyRequest, MarketDataStrategyRequestFactory, MarketDataStrategyResponse}
import com.jworkdev.trading.robot.domain.*
import com.jworkdev.trading.robot.market.data.MarketDataProvider
import com.jworkdev.trading.robot.strategy.*
import com.typesafe.scalalogging.Logger
import zio.{Task, ZIO}

import java.time.ZonedDateTime

case class TradingExecutorRequest(
    balancePerFinInst: Double,
    finInstrumentMap: Map[FinInstrument, List[Position]],
    tradingStrategies: List[TradingStrategy],
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
    tradingStrategyExecutorMap: Map[TradingStrategyType, TradingStrategyExecutor]
) extends TradingExecutorService:
  private val logger = Logger(classOf[TradingExecutorServiceImpl])

  override def execute(
      request: TradingExecutorRequest
  ): Task[List[Order]] =
    val finInstruments = request.finInstrumentMap.keys
    val input = request.tradingStrategies.flatMap(tradingStrategy =>
      finInstruments.map(finInstrument => (tradingStrategy, finInstrument, request.finInstrumentMap(finInstrument)))
    )
    for
      _ <- ZIO.attempt(
        logger.info(
          s"Trading on  ${finInstruments.map(_.symbol)} using ${request.tradingStrategies}"
        )
      )
      fibers <- ZIO.foreach(input) {
        case (tradingStrategy: TradingStrategy, finInstrument: FinInstrument, openPositions: List[Position]) =>
          val tradingExchange = request.exchangeMap(finInstrument.exchange)
          executeStrategy(
            balancePerFinInst = request.balancePerFinInst,
            finInstrument = finInstrument,
            tradingStrategy = tradingStrategy,
            openPosition =
              findOpenPositionForStrategy(openPositions = openPositions, tradingStrategy = tradingStrategy),
            tradingExchange = tradingExchange,
            strategyConfigurations = request.strategyConfigurations,
            stopLossPercentage = request.stopLossPercentage,
            tradingMode = request.tradingMode,
            tradeDateTime = request.tradingDateTime
          ).fork
      }
      results <- ZIO.foreach(fibers)(_.join)
    yield results.flatten

  private def executeStrategy(
                               balancePerFinInst: Double,
                               finInstrument: FinInstrument,
                               tradingStrategy: TradingStrategy,
                               openPosition: Option[Position],
                               tradingExchange: TradingExchange,
                               strategyConfigurations: StrategyConfigurations,
                               stopLossPercentage: Int,
                               tradingMode: TradingMode,
                               tradeDateTime: ZonedDateTime
  ): Task[Option[Order]] =
    val tradingStrategyExecutor = tradingStrategyExecutorMap(tradingStrategy.`type`)
    for
      _ <- ZIO.attempt(logger.info(s"Executing ${finInstrument.symbol} ..."))
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
      _ <- ZIO.attempt(logger.info(s"Data for ${finInstrument.symbol} fetched!"))
      order <- currentPriceRes.fold(
        ex =>
          ZIO.attempt(logger.error("Error fetching current price!", ex))
          ZIO.none
        ,
        currentPrice =>
          openPosition match
            case Some(position) =>
              ZIO.attempt(
                tradingStrategyExecutor.executeExit(tradingStrategyExitRequest =
                  TradingStrategyExitRequest(
                    position = position,
                    finInstrument = finInstrument,
                    exchange = tradingExchange,
                    tradingStrategy = tradingStrategy,
                    tradingMode = tradingMode,
                    stopLossPercentage = stopLossPercentage,
                    tradingPrice = currentPrice,
                    tradeDateTime = tradeDateTime, 
                    marketDataStrategyResponse = marketDataStrategyResponse
                  )
                )
              )
            case None =>
              ZIO.attempt(
                tradingStrategyExecutor.executeEntry(tradingStrategyEntryRequest =
                  TradingStrategyEntryRequest(
                    balancePerFinInst = balancePerFinInst,
                    finInstrument = finInstrument,
                    exchange = tradingExchange,
                    tradingStrategy = tradingStrategy,
                    tradingMode = tradingMode,
                    tradingPrice = currentPrice,
                    tradeDateTime = tradeDateTime,
                    marketDataStrategyResponse = marketDataStrategyResponse
                  )
                )
              )
      )
    yield order

  private def findOpenPositionForStrategy(
      tradingStrategy: TradingStrategy,
      openPositions: List[Position]
  ): Option[Position] =
    openPositions.find(position => tradingStrategy.`type` == position.tradingStrategyType)

  

object TradingExecutorService:
  def apply(): TradingExecutorService =
    val orderFactory = OrderFactory(
      signalFinderStrategy = SignalFinderStrategy(),
      forcePositionExitService = ForcePositionExitService())
    new TradingExecutorServiceImpl(
      MarketDataProvider(),
      MarketDataStrategyProvider(),
      MarketDataStrategyRequestFactory(),
      tradingStrategyExecutorMap = Map(
        TradingStrategyType.MACD -> new MACDTradingStrategyExecutor(orderFactory = orderFactory),
        TradingStrategyType.OpenGap -> new OpenGapTradingStrategyExecutor(orderFactory = orderFactory)
      )
    )
