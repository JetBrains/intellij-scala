package org.jetbrains.bsp

import java.io.File
import java.net.URI
import java.nio.file._

import ch.epfl.scala.bsp.endpoints
import ch.epfl.scala.bsp.schema.{BuildClientCapabilities, InitializeBuildParams, InitializedBuildParams}
import com.intellij.openapi.components.AbstractProjectComponent
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.net.NetUtils
import com.typesafe.scalalogging.Logger
import monix.eval.Task
import monix.execution.Scheduler
import monix.reactive.Observable
import org.jetbrains.bsp.BspServerConnector._
import org.langmeta.jsonrpc.{BaseProtocolMessage, Services}
import org.langmeta.lsp.{LanguageClient, LanguageServer}
import org.scalasbt.ipcsocket.UnixDomainSocket
import org.slf4j.LoggerFactory

import scala.concurrent.duration._
import scala.sys.process.Process
import scala.util.Random

class BspCommunication(project: Project) extends AbstractProjectComponent(project) {
  // TODO support persistent sessions for more features!
  // * quicker response times
  // * background compilation and error reporting/highlighting
}

class BspSession(messages: Observable[BaseProtocolMessage],
                 private implicit val client: LanguageClient,
                 initializedBuildParams: InitializeBuildParams,
                 cleanup: Task[Unit]
                ) {

  // TODO should use IDEA logging
  private val logger = Logger(LoggerFactory.getLogger(classOf[BspCommunication]))

  /** Task starts client-server connection and connects message stream.
    * @param services services that are used to listen to build notifications.
    */
  def run[T](services: Services, task: LanguageClient => Task[T])(implicit scheduler: Scheduler): Task[T] = {
    val runningClientServer = startClientServer(services)

    val whenDone = Task {
      logger.info("closing bsp connection")
      runningClientServer.cancel()
    }

    val resultTask = for {
      initResult <- initRequest
      _ = endpoints.Build.initialized.notify(InitializedBuildParams())
      result <- task(client).delayExecution(100.millis) // FIXME hackaround to "ensure" bsp server is ready to answer requests after initialized notification
      // FIXME the bsp process will just stay around zombily for, like, ever. shut it down!!!
    } yield {
      result
    }

    resultTask
      .doOnCancel(whenDone)
      .doOnFinish {
        case Some(err) =>
          logger.error("bsp connection error", err)
          whenDone
        case None => whenDone
      }
  }

  private val initRequest =
    endpoints.Build.initialize.request(initializedBuildParams)

  private def startClientServer(services: Services)(implicit scheduler: Scheduler) = {
    val server = new LanguageServer(messages, client, services, scheduler, logger)
    server.startTask
      .doOnFinish { errOpt =>
        for {
          cleaned <- cleanup
        } yield {
          logger.info("client/server closed")
          errOpt.foreach { err =>
            logger.info(s"client/server closed with error: $err")
          }
        }
      }
      .doOnCancel(cleanup)
      .runAsync
  }

}

object BspCommunication {


  def prepareSession(base: File)(implicit scheduler: Scheduler): Task[BspSession] = {

    // .bsp directory -> use GenericConnector


    val initParams = InitializeBuildParams(
      rootUri = base.toString,
      Some(BuildClientCapabilities(List("scala","java")))
    )

    val id = java.lang.Long.toString(Random.nextLong(), Character.MAX_RADIX)

    val tcpMethod = TcpBsp(new URI("localhost"), findFreePort(5001))

    val preferredMethod =
      if (SystemInfo.isWindows) WindowsLocalBsp(id)
      else if (SystemInfo.isUnix) {
        val tempDir = Files.createTempDirectory("bsp-")
        val socketFilePath = tempDir.resolve(s"$id.socket")
        val socketFile = socketFilePath.toFile
        socketFile.deleteOnExit()
        UnixLocalBsp(socketFile)
      }
      else tcpMethod


    val bloopConfigDir = new File(base, ".bloop").getCanonicalFile

    val connector =
      if (bloopConfigDir.exists()) new BloopConnector(base, initParams)
      else {
        // TODO need a protocol to detect generic bsp server
        new GenericConnector(base, initParams)
      }

    connector.connect(preferredMethod, tcpMethod)
  }


  private def findFreePort(port: Int): Int = {
    val port = 5001
    if (NetUtils.canConnectToSocket("localhost", port)) port
    else NetUtils.findAvailableSocketPort()
  }

}

abstract class BspServerConnector(initParams: InitializeBuildParams) {
  /**
    * Connect to a bsp server with one of the given methods.
    * @param methods methods supported by the bsp server, in order of preference
    * @return None if no compatible method is found. TODO should be an error response
    */
  def connect(methods: BspConnectionMethod*): Task[BspSession]
}

object BspServerConnector {
  sealed abstract class BspConnectionMethod
  final case class UnixLocalBsp(socketFile: File) extends BspConnectionMethod
  final case class WindowsLocalBsp(pipName: String) extends BspConnectionMethod
  final case class TcpBsp(host: URI, port: Int) extends BspConnectionMethod
}

/** TODO Connects to a bsp server based on information in .bsp directory */
class GenericConnector(base: File, initParams: InitializeBuildParams) extends BspServerConnector(initParams) {

  override def connect(methods: BspConnectionMethod*): Task[BspSession] = Task.raiseError(new Exception("unknown bsp servers not supported yet"))
}

class BloopConnector(base: File, initParams: InitializeBuildParams)(implicit scheduler: Scheduler) extends BspServerConnector(initParams) {

  // TODO should use IDEA logging
  val logger = Logger(LoggerFactory.getLogger(classOf[BloopConnector]))

  override def connect(methods: BspConnectionMethod*): Task[BspSession] = {
    val socketAndCleanup = methods.collectFirst {
      case UnixLocalBsp(socketFile) =>
        for {
          socket <- connectUnixSocket(socketFile)
        } yield {

          def cleanup: Task[Unit] =
            Task.eval {
              socket.close()
              socket.shutdownInput()
              socket.shutdownOutput()
              if (socketFile.isFile) socketFile.delete()
            }

          (socket, cleanup)
        }


      case TcpBsp(host, uri) =>
        for {
          socket <- connectTcp(host, uri)
        } yield (socket, Task.now(()))

      // case WindowsLocalBsp() => TODO support windows pipes connection
    }

    // TODO cleaner error reporting
    val socketOrError = socketAndCleanup.getOrElse(Task.raiseError(new Exception("no supported connection method available")))

    for {
      socketAndCleanup <- socketOrError
    } yield {
      val socket = socketAndCleanup._1
      val cleanup = socketAndCleanup._2

      val client: LanguageClient = new LanguageClient(socket.getOutputStream, logger)
      val messages = BaseProtocolMessage.fromInputStream(socket.getInputStream)
      new BspSession(messages, client, initParams, cleanup)
    }
  }

  private def connectUnixSocket(socketFile: File) = {

    val bloopCommand = s"bloop bsp --protocol local --socket $socketFile --verbose"

    val bspReady = Task {
      var sockfileCreated = false
      while (!sockfileCreated) {
        // TODO bail out after some time
        Thread.sleep(10)
        sockfileCreated = socketFile.exists()
      }
      sockfileCreated
    }

    // TODO kill bloop process on cancel / error

    for {
      p <- Task(runBloop(bloopCommand))
      _ <- bspReady
    } yield new UnixDomainSocket(socketFile.getCanonicalPath)
  }

  private def connectTcp(host: URI, port: Int): Task[java.net.Socket] = {
    val bloopCommand = s"bloop bsp --protocol tcp --host ${host.toString} --port $port --verbose"

    Task {
      runBloop(bloopCommand)
      new java.net.Socket(host.toString, port)
    }
  }

  private def runBloop(command: String) =
    Process(command, base).run

}