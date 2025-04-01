package org.jetbrains.sbt.shell

import java.io.{BufferedReader, File, InputStreamReader, PrintWriter}
import java.util.concurrent.atomic.AtomicInteger
import play.api.libs.json._

import jnr.unixsocket.{UnixSocketAddress, UnixSocketChannel}
import ServerUtil._

import java.util.concurrent.LinkedBlockingQueue
import scala.io.StdIn


object SbtServerClient {
  private val idCounter = new AtomicInteger(0)

  private[shell] val queue = new LinkedBlockingQueue[String]()

  private[shell] var done: Boolean = false


  def main(args: Array[String]): Unit = {
    if (args.length < 1)
      showUsageAndExit()

    val projectPath = args(0)
    findPortFile(projectPath) match {
      case Some(connection) => runInteractiveSession(connection)
      case None => println(s"No active sbt server found for project at $projectPath")
    }
  }

  private def runInteractiveSession(connection: SbtConnection): Unit = {
    println(s"Found sbt server socket at ${connection.socketPath}")
    println("Enter commands (compile, test, run, shutdown) or press Ctrl+C to quit:")

    val result = for {
      channel <- createSocketChannel(connection)
      session <- setupClientSession(channel)
      _ <- runCommandLoop(session)
    } yield ()

    result.fold(
      error => println(s"Error: $error"),
      _ => println("Session ended normally")
    )
  }

  private def createSocketChannel(connection: SbtConnection): Either[String, UnixSocketChannel] = {
    try {
      val socketFile = new File(connection.socketPath)
      val address = new UnixSocketAddress(socketFile)
      Right(UnixSocketChannel.open(address))
    } catch {
      case e: Exception => Left(s"Failed to open socket: ${e.getMessage}")
    }
  }

  private def setupClientSession(channel: UnixSocketChannel): Either[String, ClientSession] = {
    try {
      val socket = channel.socket()
      val out = new PrintWriter(socket.getOutputStream, true)
      val in = new BufferedReader(new InputStreamReader(socket.getInputStream))

      val listener = new Listener(in)
      val processer = new ResponseProcesser()

      Right(ClientSession(channel, out, in, listener, processer))
    } catch {
      case e: Exception => Left(s"Failed to setup session: ${e.getMessage}")
    }
  }

  private def runCommandLoop(session: ClientSession): Either[String, Unit] = {
    def getUserInput: Either[String, Unit] = {
      try {
        val command = StdIn.readLine()
        command match {
          case "shutdown" =>
            println("Exiting SBT client...")
            closeConnection(session.channel, session.listener, session.processer)
            Right(())
          case cmd if SbtCommand.isValid(cmd) =>
            sendCommand(session.out, cmd)
            getUserInput
          case _ =>
            println(s"Unknown command: $command")
            println(s"Available commands: ${SbtCommand.availableCommands.mkString(", ")}")
            getUserInput
        }
      } catch {
        case e: Exception => Left(s"Error processing command: ${e.getMessage}")
      }
    }
    session.listener.start()
    session.processer.start()

    sendCommand(session.out, "init")

    getUserInput
  }

  private def sendCommand(out: PrintWriter, command: String): Unit = {
    val id = idCounter.incrementAndGet().toString

    SbtCommand.fromString(command) match {
      case Some(sbtCommand) =>
        val jsonCommand = sbtCommand.createCommand(id)
        val jsonStr = Json.stringify(jsonCommand)
        val request = s"Content-Length: ${jsonStr.length}\r\n\r\n$jsonStr"

        println(s"Sending command: ${sbtCommand.name}")
        out.print(request)
        out.flush()
      case None =>
        println(s"Unknown command: $command")
        println(s"Available commands: ${SbtCommand.availableCommands.mkString(", ")}")
    }
  }

  private def closeConnection(channel: UnixSocketChannel, listener: Listener, processer: ResponseProcesser): Unit = {
    try {
      done = true
      channel.close()
      listener.interrupt()
      processer.interrupt()
      println("Connection closed")
    }
    catch { case _: Exception => }
  }
}