package org.jetbrains.bsp.protocol

import java.io.File
import java.net.{Socket, URI}

import ch.epfl.scala.bsp4j._
import com.intellij.openapi.diagnostic.Logger
import org.jetbrains.bsp.protocol.BspServerConnector.{BspCapabilities, BspConnectionMethod, TcpBsp, UnixLocalBsp}
import org.jetbrains.bsp.{BspError, BspErrorMessage, BspException}
import org.scalasbt.ipcsocket.UnixDomainSocket

import scala.collection.JavaConverters._
import scala.sys.process.{Process, ProcessLogger}
import scala.util.Try

class BloopConnector4j(bloopExecutable: File, base: File, capabilities: BspCapabilities)
  extends BspServerConnectorSync(base.getCanonicalFile.toURI, capabilities) {

  private val logger: Logger = Logger.getInstance(classOf[BloopConnector4j])
  private val verbose = false

  override def connect(methods: BspConnectionMethod*): Either[BspError, Bsp4jSession] = {

    val socketAndCleanupOpt: Option[Either[BspError, (Socket, ()=>Unit)]] = methods.collectFirst {
      case UnixLocalBsp(socketFile) =>

        val socketResult = connectUnixSocket(socketFile)

        socketResult.map { socket =>
          val cleanup: ()=>Unit = () => {
            socket.close()
            socket.shutdownInput()
            socket.shutdownOutput()
            if (socketFile.isFile) socketFile.delete()
          }

          (socket, cleanup)
        }

      case TcpBsp(host, uri) =>
        val socketResult = connectTcp(host, uri)
        socketResult.map { socket =>
          (socket, ()=>())
        }

      // case WindowsLocalBsp(pipeName) => // TODO support windows pipes connection

    }

    val socketAndCleanupEither = socketAndCleanupOpt.getOrElse(Left(BspErrorMessage("could not find supported connection method for bloop")))

    socketAndCleanupEither.map { socketAndCleanup =>
      val (socket, cleanup) = socketAndCleanup
      val buildClientCapabilities = new BuildClientCapabilities(capabilities.languageIds.asJava, capabilities.providesFileWatching)
      val initializeBuildParams = new InitializeBuildParams(rootUri.toString, buildClientCapabilities)
      new Bsp4jSession(socket.getInputStream, socket.getOutputStream, initializeBuildParams, cleanup)
    }
  }

  private def connectUnixSocket(socketFile: File): Either[BspErrorMessage, UnixDomainSocket] = {

    val verboseParam = if (verbose) "--verbose" else ""
    val bloopParams = s"bsp --protocol local --socket $socketFile $verboseParam"
    runBloop(bloopParams)

    if (bspReady(socketFile)) {
      Right(new UnixDomainSocket(socketFile.getCanonicalPath))
    } else {
      // TODO kill bloop process on cancel / error
      Left(BspErrorMessage("Bloop did not create socket file. Is the server running?"))
    }
  }

  private def bspReady(socketFile: File) = {
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

  private def connectTcp(host: URI, port: Int): Either[BspError, java.net.Socket] = {
    val verboseParam = if (verbose) "--verbose" else ""
    val bloopParams = s"bsp --protocol tcp --host ${host.toString} --port $port $verboseParam"

    runBloop(bloopParams)
    Try(new java.net.Socket(host.toString, port))
      .toEither.left.map(err => BspException("bloop tcp connection failed", err))
  }

  private val proclog = ProcessLogger(
    out => logger.debug(s"bloop: $out"),
    err => logger.warn(s"bloop: $err")
  )

  private def runBloop(params: String) = {
    val command = s"${bloopExecutable.getCanonicalPath} $params"
    Process(command, base).run(proclog)
  }

}

object BloopConnector4j {

}