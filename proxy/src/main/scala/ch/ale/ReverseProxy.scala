package ch.ale.reverseproxy

import scala.concurrent.duration._
import spray.httpx.encoding._
import akka.pattern.ask
import akka.util.Timeout
import akka.actor._
import spray.can.Http
import spray.can.server.Stats
import spray.util._
import spray.http._
import HttpMethods._
import MediaTypes._
import spray.can.Http.RegisterChunkHandler
import scala.util.Success
import scala.util.Failure
import org.jsoup.Jsoup

class ProxyActor extends Actor with ActorLogging with ClientReq {
  implicit val timeout: Timeout = 1.second // for the actor 'asks'
  import context.dispatcher // ExecutionContext for the futures and scheduler
  import context.system

  val clientSys = ActorSystem("client")

  def receive = {
    // when a new connection comes in we register ourselves as the connection handler
    case _: Http.Connected => sender ! Http.Register(self)
    // Main Handler
    case HttpRequest(GET, path, headers, in2, in3) =>

      val prefix = path.toRelative.toString.toString().split('/')

      val backendHost: Option[Uri] =
        if (prefix.length > 2)
          mapping.get((prefix(1), prefix(2)))
        else None

      val codec = Gzip({ x =>
        if (x.isResponse) true
        else false
      })
      backendHost match {

        case Some(x) => {
          val originalsender = sender
          val rpprefix = "/" + prefix(1) + "/" + prefix(2)
          println("Requestpath is: " + prefix.drop(3).mkString("/"))
          val resp = request(x.toString(), prefix.drop(3).mkString("/"))(clientSys)

          resp onComplete {

            case Success(res) => {
              import spray.http.HttpHeaders._
              import spray.http.StatusCodes._

              val contentType = {
                res.headers.filter { x =>
                  x match {
                    case `Content-Type`(ct) => true
                    case _ => false
                  }
                } match {
                  case head :: Nil => head match {
                    case `Content-Type`(ct) => ct
                    case _ => ContentType(MediaTypes.`text/html`)
                  }
                  case _ => ContentType(MediaTypes.`text/html`)
                }
              }
              println(contentType + "  IS THE CONTENT TYPE")

              val encoding = res.encoding.value
              //Decode body
              val msg = encoding match {
                case "gzip" => codec.decode(res)
                case "deflate" => codec.decode(res)
                case _ => res
              }

              // Mapping Urls for HTML
              val mappedMsg = msg.mapEntity { entity =>
                import scala.collection.JavaConversions._
                import spray.http.HttpHeaders._
                HttpEntity(contentType, {

                  val parsed = Jsoup.parse(entity.asString)
                  asScalaIterator(parsed.select("a").iterator()).foreach { a =>
                    val href = a.attr("href")
                    if (!href.startsWith("http") || !href.startsWith("#") || !href.isEmpty()) a.attr("href", rpprefix + href)
                  }
                  asScalaIterator(parsed.select("link").iterator()).foreach { script =>
                    val href = script.attr("href")
                    if (!href.startsWith("http") || !href.isEmpty()) script.attr("href", rpprefix + href)
                  }
                  asScalaIterator(parsed.select("script").iterator()).foreach { script =>
                    val src = script.attr("src")
                    if (!src.isEmpty() || !src.startsWith("http")) script.attr("src", rpprefix + src)
                  }
                  asScalaIterator(parsed.select("img").iterator()).foreach { img =>
                    val src = img.attr("src")
                    if (!src.startsWith("http")) img.attr("src", rpprefix + src)

                  }
                  println(parsed)
                  parsed.html()
                })
              }

              import spray.http.StatusCodes._
              val finalMsg =
                if (mappedMsg.status == PermanentRedirect || mappedMsg.status == NotModified)
                  mappedMsg.mapHeaders { x =>
                    if (x.contains("Location")) {
                      println(x + " FIRST")
                      x
                    } else {
                      println(x + " SECOND")
                      x
                    }

                  }
                else mappedMsg

              //encode body
              val encodedmsg = encoding match {
                case "gzip" => {
                  codec.encode(finalMsg)
                }

                case "deflate" => codec.encode(finalMsg)
                case _ => finalMsg
              }

              println("HEADERR")

              originalsender ! encodedmsg

            }
            case Failure(error) => {
              log.warning("Error: {}", error)
              sender ! index
            }
          }
        }
        case None => sender ! index
      }

    case _: HttpRequest => sender ! HttpResponse(status = 404, entity = "Unknown resource!")

    case Timedout(HttpRequest(_, Uri.Path("/timeout/timeout"), _, _, _)) =>
      log.info("Dropping Timeout message")

    case Timedout(HttpRequest(method, uri, _, _, _)) =>
      sender ! HttpResponse(
        status = 500,
        entity = "The " + method + " request to '" + uri + "' has timed out...")
  }

  lazy val index = HttpResponse(
    entity = HttpEntity(`text/html`,
      <html>
        <body>
          <h1>Say hello to <i>spray-can</i>!</h1>
          <p>Defined resources:</p>
          <ul>
            <li><a href="/ping">/ping</a></li>
          </ul>
        </body>
      </html>.toString()))

  lazy val mapping = Map[(String, String), Uri](
    ("LLL", "Domain") -> Uri("www.google.com"),
    ("ehehehe", "heheh") -> Uri("spray.io"))
}
