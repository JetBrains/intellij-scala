package org.jetbrains.bsp.protocol

import java.io.File
import java.net.URI

import ch.epfl.scala.bsp._
import com.intellij.openapi.diagnostic.Logger
import monix.eval.Task
import monix.execution.Scheduler
import org.jetbrains.bsp.{BspError, BspErrorMessage, BspException}
import org.jetbrains.bsp.BspUtil.IdeaLoggerOps
import org.jetbrains.bsp.protocol.BspServerConnector._
import org.scalasbt.ipcsocket.UnixDomainSocket

import scala.meta.jsonrpc._
import scala.sys.process.{Process, ProcessLogger}
import scala.util.Try

class BloopConnector(bloopExecutable: File, base: File, capabilities: BspCapabilities)
                    (implicit scheduler: Scheduler)
  extends BspServerConnector(base.getCanonicalFile.toURI, capabilities) {

  private val logger: Logger = Logger.getInstance(classOf[BloopConnector])

  override def connect(methods: BspConnectionMethod*): Task[Either[BspError, Bsp4sSession]] = {

    val socketAndCleanupMethod = methods.collectFirst {
      case UnixLocalBsp(socketFile) =>
        for {
          socketResult <- connectUnixSocket(socketFile)
        } yield {

          socketResult.map { socket =>
            def cleanup: Task[Unit] =
              Task.eval {
                socket.close()
                socket.shutdownInput()
                socket.shutdownOutput()
                if (socketFile.isFile) socketFile.delete()
              }

            (socket, cleanup)
          }
        }

      case TcpBsp(host, uri) =>
        for {
          socketResult <- connectTcp(host, uri)
        } yield socketResult.map { socket =>
          (socket, Task.now(()))
        }

      // case WindowsLocalBsp(pipeName) => // TODO support windows pipes connection

    }

    val socketAndCleanupTask = socketAndCleanupMethod match {
      case None => Task.now(Left(BspErrorMessage("could not find supported connection method for bloop")))
      case Some(task) => task
    }

    for {
      socketAndCleanupEither <- socketAndCleanupTask
    } yield socketAndCleanupEither.map { socketAndCleanup =>
      val (socket, cleanup) = socketAndCleanup
      val sLogger = logger.toScribeLogger
      val client: LanguageClient = new LanguageClient(socket.getOutputStream, sLogger)
      val messages = BaseProtocolMessage.fromInputStream(socket.getInputStream, sLogger)
      val buildClientCapabilities = BuildClientCapabilities(capabilities.languageIds, capabilities.providesFileWatching)
      val initializeBuildParams = InitializeBuildParams(Uri(rootUri.toString), buildClientCapabilities)
      new Bsp4sSession(messages, client, initializeBuildParams, cleanup, scheduler)
    }
  }

  private def connectUnixSocket(socketFile: File) = {

    val bloopParams = s"bsp --protocol local --socket $socketFile"

    val bspReady = Task {
      var sockfileCreated = false
      var timeout = 12000
      val wait = 10
      while (!sockfileCreated && timeout > 0) {
        // we expect this wait to be short in most cases, so it's ok to block thread
        Thread.sleep(wait)
        timeout -= wait
        sockfileCreated = socketFile.exists()
      }
      sockfileCreated
    }

    // TODO kill bloop process on cancel / error

    for {
      p <- Task(runBloop(bloopParams))
      isReady <- bspReady
    } yield
      if (isReady) Right(new UnixDomainSocket(socketFile.getCanonicalPath))
      else Left(BspErrorMessage("Bloop did not create socket file. Is the server running?"))
  }

  private def connectTcp(host: URI, port: Int): Task[Either[BspError, java.net.Socket]] = {
    val bloopParams = s"bsp --protocol tcp --host ${host.toString} --port $port"

    Task {
      runBloop(bloopParams)
      Try(new java.net.Socket(host.toString, port))
        .toEither.left.map(err => BspException("bloop tcp connection failed", err))
    }
  }

  private val proclog = ProcessLogger(
    out => logger.debug(s"bloop: $out"),
    err => logger.error(s"bloop: $err")
  )

  private def runBloop(params: String) = {
    val command = s"${bloopExecutable.getCanonicalPath} $params"
    Process(command, base).run(proclog)
  }

}

