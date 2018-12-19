package com.pagerduty.arrivals.impl

import akka.actor.ActorSystem
import akka.http.scaladsl.{Http, HttpExt}
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.ws.{Message, WebSocketRequest}
import akka.stream.Materializer
import akka.stream.scaladsl.Flow
import com.pagerduty.arrivals.api.proxy.HttpClient
import com.pagerduty.arrivals.impl.proxy.HttpProxy
import com.pagerduty.metrics.{Metrics, NullMetrics}

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._

object ArrivalsContext {
  def buildHttpClient(http: HttpExt, materializer: Materializer): HttpClient = {
    new HttpClient {
      override def executeRequest(request: HttpRequest) = http.singleRequest(request)
      override def executeWebSocketRequest[T](request: WebSocketRequest, clientFlow: Flow[Message, Message, T]) =
        http.singleWebSocketRequest(request, clientFlow)(materializer)
    }
  }
}

case class ArrivalsContext[AddressingConfig](
    addressingConfig: AddressingConfig,
    entityConsumptionTimeout: FiniteDuration = 20.seconds,
    buildHttpClient: (HttpExt, Materializer) => HttpClient = ArrivalsContext.buildHttpClient
  )(implicit actorSystem: ActorSystem,
    val materializer: Materializer,
    val metrics: Metrics = NullMetrics) {

  implicit val executionContext = actorSystem.dispatcher

  val http = Http()

  val httpClient = buildHttpClient(http, materializer)

  val httpProxy = new HttpProxy(addressingConfig, httpClient)

  def shutdown(): Unit = {
    http.shutdownAllConnectionPools()
  }
}
