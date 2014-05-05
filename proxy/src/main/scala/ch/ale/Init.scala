package ch.ale.reverseproxy

import java.io.File
import org.parboiled.common.FileUtils
import scala.concurrent.duration._
import akka.actor._
import akka.pattern.ask
import akka.actor.ActorSystem
import spray.routing.{ HttpService, RequestContext }
import spray.routing.directives.CachingDirectives
import spray.httpx.marshalling.Marshaller
import spray.httpx.encoding.Gzip
import spray.util._
import spray.http._
import MediaTypes._
import CachingDirectives._
import akka.actor.{ ActorSystem, Props }
import akka.io.IO
import spray.can.Http
import spray.routing.HttpService
import spray.can.Http
import spray.client.pipelining._
import scala.concurrent.Future
import spray.httpx.encoding._

object Proxy {

  def main(args: Array[String]): Unit = {
    // we need an ActorSystem to host our application in
    implicit val system = ActorSystem("on-spray-can")

    // create and start our service actor
    val service = system.actorOf(Props[ProxyActor], "demo-service")

    // start a new HTTP server on port 8080 with our service actor as the handler
    IO(Http) ! Http.Bind(service, "localhost", port = 8080)
  }
}

