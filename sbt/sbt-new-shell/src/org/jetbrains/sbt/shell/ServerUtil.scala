package org.jetbrains.sbt.shell

import jnr.unixsocket.UnixSocketChannel
import play.api.libs.json.Json

import java.io.{BufferedReader, PrintWriter}
import java.nio.file.{Files, Paths}
import scala.util.{Failure, Success, Try}

object ServerUtil {

  case class ClientSession(
    channel: UnixSocketChannel,
    out: PrintWriter,
    in: BufferedReader,
    listener: Listener,
    processer: ResponseProcesser
  )

  private[shell] case class SbtConnection(socketPath: String)

  def findPortFile(projectPath: String): Option[SbtConnection] = {
    val portfilePath = Paths.get(projectPath, "project", "target", "active.json")
    println(portfilePath)
    if (Files.exists(portfilePath)) {
      Try {
        val content = Files.readString(portfilePath)
        val json = Json.parse(content)
        val uri = (json \ "uri").as[String]
        if (uri.startsWith("local:///")) {
          val socketPath = uri.substring("local://".length)
          Some(SbtConnection(socketPath))
        } else {
          println(s"Unsupported URI format for domain socket: $uri")
          None
        }
      } match {
        case Success(conn) => conn
        case Failure(e) =>
          println(s"Failed to parse portfile: ${e.getMessage}")
          None
      }
    } else {
      println(s"Portfile not found at ${portfilePath.toAbsolutePath}")
      None
    }
  }

  def showUsageAndExit(): Nothing = {
    println("Usage: SbtServerClient <project-path>")
    println("Available commands: compile, test, run, shutdown, exit")
    System.exit(1)
    throw new RuntimeException("Should not reach here")
  }

  def stripContentLengthSuffix(response: String): String = {
    val contentLengthSuffixPattern = """(.*?)(Content-Length: \d+)?\s*$""".r

    response match {
      case contentLengthSuffixPattern(content, _) => content.trim
      case _ => response.trim
    }
  }
}

