package com.jworkdev.trading.robot.data.ib

import com.ib.client.*
import com.ib.controller.ApiConnection.ILogger
import com.jworkdev.trading.robot.data.{
  StockPrice,
  StockQuoteFrequency,
  StockQuoteInterval,
  StockQuotesDataProvider
}
import com.typesafe.scalalogging.Logger

import java.time.Instant
import java.util
import java.util.concurrent.{Semaphore, TimeUnit}
import java.util.{Map, Set}
import scala.collection.mutable
import scala.util.{Failure, Success, Try}

class IBStockQuotesDataProvider(host: String, port: Int)
    extends StockQuotesDataProvider,
      EWrapper:
  private val logger = Logger(classOf[IBStockQuotesDataProvider])
  private val ibLogger: ILogger = (message: String) => logger.info(message)
  private val semaphore = new Semaphore(1, true)
  private var isConnected: Boolean = false
  private val signal = new EJavaSignal();
  private val clientSocket = new EClientSocket(this, signal)
  private val reader = new EReader(clientSocket, signal)
  private val tickerPrices = mutable.ListBuffer.empty[(String, Double)]
  private var currentTicket: Option[String] = None
  private var nextOrderId = 0

  private def connect(): Unit =
    if !isConnected then
      logger.info("Connecting ...")
      clientSocket.eConnect(host, port, 0)
      reader.start()
      new Thread(() =>
        while clientSocket.isConnected do
          logger.info("waiting for signal ...")
          signal.waitForSignal()
          try
            logger.info("processing messages ...")
            reader.processMsgs()
          catch
            case ex: Exception =>
              logger.error("Exception: ", ex)
      ).start()
      semaphore.acquire()

  def getIntradayQuotes(
      symbol: String,
      interval: StockQuoteInterval
  ): Try[List[StockPrice]] = ???

  def getQuotes(
      symbol: String,
      frequency: StockQuoteFrequency
  ): Try[List[StockPrice]] = ???

  def getCurrentInfo(symbol: String): Try[StockPrice] =
    connect()
    semaphore.tryAcquire(5, TimeUnit.SECONDS)
    try Thread.sleep(1000)
    catch
      case e: InterruptedException =>
        e.printStackTrace()
    if clientSocket.isConnected then
      logger.info(s"Requesting $symbol ...")
      val contract = new Contract()
      contract.symbol(symbol)
      contract.secType("STK")
      contract.currency("USD")
      contract.exchange("SMART")
      currentTicket = Some(symbol)
      clientSocket.reqMktData(1001, contract, "", false, false, null);
      Success(
        StockPrice(
          symbol = "",
          open = 1.0,
          close = 2.0,
          high = 3,
          low = 4,
          volume = 4,
          snapshotTime = Instant.now()
        )
      )
    else
      Failure(new IllegalStateException(s"Cannot Connect with ${host}:${port}"))
  def getHistory(
      symbol: String,
      from: Instant,
      to: Instant
  ): Try[List[StockPrice]] = ???

  def release(): Unit =
    logger.info("Disconnecting ...")
    clientSocket.eDisconnect()

  override def tickPrice(
      tickerId: Int,
      field: Int,
      price: Double,
      attrib: TickAttrib
  ): Unit =
    println("Receiving...")
    if field eq TickType.LAST then
      System.out.println("Current price of AAPL: $" + price)
    val ticketPrice: (String, Double) = (TickType.getField(field), price)
    tickerPrices += ticketPrice

  override def tickSize(tickerId: Int, field: Int, size: Decimal): Unit =
    println(size)

  override def tickOptionComputation(
      tickerId: Int,
      field: Int,
      tickAttrib: Int,
      impliedVol: Double,
      delta: Double,
      optPrice: Double,
      pvDividend: Double,
      gamma: Double,
      vega: Double,
      theta: Double,
      undPrice: Double
  ): Unit = {}

  override def tickGeneric(
      tickerId: Int,
      tickType: Int,
      value: Double
  ): Unit = {}

  override def tickString(
      tickerId: Int,
      tickType: Int,
      value: String
  ): Unit = {}

  override def tickEFP(
      tickerId: Int,
      tickType: Int,
      basisPoints: Double,
      formattedBasisPoints: String,
      impliedFuture: Double,
      holdDays: Int,
      futureLastTradeDate: String,
      dividendImpact: Double,
      dividendsToLastTradeDate: Double
  ): Unit = {}

  override def orderStatus(
      orderId: Int,
      status: String,
      filled: Decimal,
      remaining: Decimal,
      avgFillPrice: Double,
      permId: Int,
      parentId: Int,
      lastFillPrice: Double,
      clientId: Int,
      whyHeld: String,
      mktCapPrice: Double
  ): Unit = {}

  override def openOrder(
      orderId: Int,
      contract: Contract,
      order: Order,
      orderState: OrderState
  ): Unit = {}

  override def openOrderEnd(): Unit = {}

  override def updateAccountValue(
      key: String,
      value: String,
      currency: String,
      accountName: String
  ): Unit = {}

  override def updatePortfolio(
      contract: Contract,
      position: Decimal,
      marketPrice: Double,
      marketValue: Double,
      averageCost: Double,
      unrealizedPNL: Double,
      realizedPNL: Double,
      accountName: String
  ): Unit = {}

  override def updateAccountTime(timeStamp: String): Unit = {}

  override def accountDownloadEnd(accountName: String): Unit = {}

  override def nextValidId(orderId: Int): Unit =
    this.nextOrderId = orderId;

  override def contractDetails(
      reqId: Int,
      contractDetails: ContractDetails
  ): Unit = {}

  override def bondContractDetails(
      reqId: Int,
      contractDetails: ContractDetails
  ): Unit = {}

  override def contractDetailsEnd(reqId: Int): Unit = {}

  override def execDetails(
      reqId: Int,
      contract: Contract,
      execution: Execution
  ): Unit = {}

  override def execDetailsEnd(reqId: Int): Unit = {}

  override def updateMktDepth(
      tickerId: Int,
      position: Int,
      operation: Int,
      side: Int,
      price: Double,
      size: Decimal
  ): Unit = {}

  override def updateMktDepthL2(
      tickerId: Int,
      position: Int,
      marketMaker: String,
      operation: Int,
      side: Int,
      price: Double,
      size: Decimal,
      isSmartDepth: Boolean
  ): Unit = {}

  override def updateNewsBulletin(
      msgId: Int,
      msgType: Int,
      message: String,
      origExchange: String
  ): Unit = {}

  override def managedAccounts(accountsList: String): Unit = {}

  override def receiveFA(faDataType: Int, xml: String): Unit = {}

  override def historicalData(reqId: Int, bar: Bar): Unit = {}

  override def scannerParameters(xml: String): Unit = {}

  override def scannerData(
      reqId: Int,
      rank: Int,
      contractDetails: ContractDetails,
      distance: String,
      benchmark: String,
      projection: String,
      legsStr: String
  ): Unit = {}

  override def scannerDataEnd(reqId: Int): Unit = {}

  override def realtimeBar(
      reqId: Int,
      time: Long,
      open: Double,
      high: Double,
      low: Double,
      close: Double,
      volume: Decimal,
      wap: Decimal,
      count: Int
  ): Unit = {}

  override def currentTime(time: Long): Unit = {}

  override def fundamentalData(reqId: Int, data: String): Unit = {}

  override def deltaNeutralValidation(
      reqId: Int,
      deltaNeutralContract: DeltaNeutralContract
  ): Unit = {}

  override def tickSnapshotEnd(reqId: Int): Unit = {}

  override def marketDataType(reqId: Int, marketDataType: Int): Unit = {}

  override def commissionReport(commissionReport: CommissionReport): Unit = {}

  override def position(
      account: String,
      contract: Contract,
      pos: Decimal,
      avgCost: Double
  ): Unit = {}

  override def positionEnd(): Unit = {}

  override def accountSummary(
      reqId: Int,
      account: String,
      tag: String,
      value: String,
      currency: String
  ): Unit = {}

  override def accountSummaryEnd(reqId: Int): Unit = {}

  override def verifyMessageAPI(apiData: String): Unit = {}

  override def verifyCompleted(
      isSuccessful: Boolean,
      errorText: String
  ): Unit = {}

  override def verifyAndAuthMessageAPI(
      apiData: String,
      xyzChallenge: String
  ): Unit = {}

  override def verifyAndAuthCompleted(
      isSuccessful: Boolean,
      errorText: String
  ): Unit = {}

  override def displayGroupList(reqId: Int, groups: String): Unit = {}

  override def displayGroupUpdated(reqId: Int, contractInfo: String): Unit = {}

  override def error(e: Exception): Unit =
    logger.error("Error ex:", e)

  override def error(str: String): Unit =
    logger.error("Error str:", str)

  override def error(
      id: Int,
      errorCode: Int,
      errorMsg: String,
      advancedOrderRejectJson: String
  ): Unit =
    logger.error(s"Error id:$id errorCode:$errorCode errorMsg:$errorMsg")

  override def connectionClosed(): Unit = {}

  override def connectAck(): Unit =
    logger.info("Connected!")
    semaphore.release()
    isConnected = true

  override def positionMulti(
      reqId: Int,
      account: String,
      modelCode: String,
      contract: Contract,
      pos: Decimal,
      avgCost: Double
  ): Unit = {}

  override def positionMultiEnd(reqId: Int): Unit = {}

  override def accountUpdateMulti(
      reqId: Int,
      account: String,
      modelCode: String,
      key: String,
      value: String,
      currency: String
  ): Unit = {}

  override def accountUpdateMultiEnd(reqId: Int): Unit = {}

  override def securityDefinitionOptionalParameter(
      reqId: Int,
      exchange: String,
      underlyingConId: Int,
      tradingClass: String,
      multiplier: String,
      expirations: util.Set[java.lang.String],
      strikes: util.Set[java.lang.Double]
  ): Unit = {}

  override def securityDefinitionOptionalParameterEnd(reqId: Int): Unit = {}

  override def softDollarTiers(
      reqId: Int,
      tiers: Array[SoftDollarTier]
  ): Unit = {}

  override def familyCodes(familyCodes: Array[FamilyCode]): Unit = {}

  override def symbolSamples(
      reqId: Int,
      contractDescriptions: Array[ContractDescription]
  ): Unit = {}

  override def historicalDataEnd(
      reqId: Int,
      startDateStr: String,
      endDateStr: String
  ): Unit = {}

  override def mktDepthExchanges(
      depthMktDataDescriptions: Array[DepthMktDataDescription]
  ): Unit = {}

  override def tickNews(
      tickerId: Int,
      timeStamp: Long,
      providerCode: String,
      articleId: String,
      headline: String,
      extraData: String
  ): Unit = {}

  override def smartComponents(
      reqId: Int,
      theMap: util.Map[Integer, util.Map.Entry[String, Character]]
  ): Unit = {}

  override def tickReqParams(
      tickerId: Int,
      minTick: Double,
      bboExchange: String,
      snapshotPermissions: Int
  ): Unit = {}

  override def newsProviders(newsProviders: Array[NewsProvider]): Unit = {}
  override def newsArticle(
      requestId: Int,
      articleType: Int,
      articleText: String
  ): Unit = {}

  override def historicalNews(
      requestId: Int,
      time: String,
      providerCode: String,
      articleId: String,
      headline: String
  ): Unit = {}

  override def historicalNewsEnd(requestId: Int, hasMore: Boolean): Unit = {}

  override def headTimestamp(reqId: Int, headTimestamp: String): Unit = {}

  override def histogramData(
      reqId: Int,
      items: util.List[HistogramEntry]
  ): Unit = {}

  override def historicalDataUpdate(reqId: Int, bar: Bar): Unit = {}

  override def rerouteMktDataReq(
      reqId: Int,
      conId: Int,
      exchange: String
  ): Unit = {}

  override def rerouteMktDepthReq(
      reqId: Int,
      conId: Int,
      exchange: String
  ): Unit = {}

  override def marketRule(
      marketRuleId: Int,
      priceIncrements: Array[PriceIncrement]
  ): Unit = {}

  override def pnl(
      reqId: Int,
      dailyPnL: Double,
      unrealizedPnL: Double,
      realizedPnL: Double
  ): Unit = {}

  override def pnlSingle(
      reqId: Int,
      pos: Decimal,
      dailyPnL: Double,
      unrealizedPnL: Double,
      realizedPnL: Double,
      value: Double
  ): Unit = {}

  override def historicalTicks(
      reqId: Int,
      ticks: util.List[HistoricalTick],
      done: Boolean
  ): Unit = {}

  override def historicalTicksBidAsk(
      reqId: Int,
      ticks: util.List[HistoricalTickBidAsk],
      done: Boolean
  ): Unit = {}

  override def historicalTicksLast(
      reqId: Int,
      ticks: util.List[HistoricalTickLast],
      done: Boolean
  ): Unit = {}

  override def tickByTickAllLast(
      reqId: Int,
      tickType: Int,
      time: Long,
      price: Double,
      size: Decimal,
      tickAttribLast: TickAttribLast,
      exchange: String,
      specialConditions: String
  ): Unit = {}

  override def tickByTickBidAsk(
      reqId: Int,
      time: Long,
      bidPrice: Double,
      askPrice: Double,
      bidSize: Decimal,
      askSize: Decimal,
      tickAttribBidAsk: TickAttribBidAsk
  ): Unit = {}

  override def tickByTickMidPoint(
      reqId: Int,
      time: Long,
      midPoint: Double
  ): Unit = {}

  override def orderBound(
      orderId: Long,
      apiClientId: Int,
      apiOrderId: Int
  ): Unit = {}

  override def completedOrder(
      contract: Contract,
      order: Order,
      orderState: OrderState
  ): Unit = {}

  override def completedOrdersEnd(): Unit = {}

  override def replaceFAEnd(reqId: Int, text: String): Unit = {}

  override def wshMetaData(reqId: Int, dataJson: String): Unit = {}

  override def wshEventData(reqId: Int, dataJson: String): Unit = {}

  override def historicalSchedule(
      reqId: Int,
      startDateTime: String,
      endDateTime: String,
      timeZone: String,
      sessions: util.List[HistoricalSession]
  ): Unit = {}

  override def userInfo(reqId: Int, whiteBrandingId: String): Unit = {}
