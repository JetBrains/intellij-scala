package org.jetbrains.bsp.protocol

import java.io.File
import java.net.URI
import java.nio.file._

import com.intellij.openapi.components.ProjectComponent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.{Project, ProjectUtil}
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.net.NetUtils
import org.jetbrains.bsp.BspError
import org.jetbrains.bsp.protocol.BspNotifications.BspNotification
import org.jetbrains.bsp.protocol.BspSession.{BspSessionTask, NotificationAggregator, NotificationCallback}
import org.jetbrains.bsp.protocol.BspCommunication._
import org.jetbrains.bsp.protocol.BspServerConnector._
import org.jetbrains.bsp.settings.BspExecutionSettings

import scala.concurrent.{Future, Promise}
import scala.util.Random

class BspCommunicationComponent(project: Project) extends ProjectComponent {

  val communication = new BspCommunication(base, executionSettings)

  private def base = new File(project.getBasePath)
  private def executionSettings = {
    val workingDirPath =
      Option(ProjectUtil.guessProjectDir(project))
        .getOrElse(throw new IllegalStateException(s"no project directory found for project ${project.getName}"))
        .getCanonicalPath
    BspExecutionSettings.executionSettingsFor(project, workingDirPath)
  }
}

class BspCommunication(base: File, executionSettings: BspExecutionSettings) {

  private val log = Logger.getInstance(classOf[BspCommunication])

  @volatile private var session: Option[BspSession] = None

  private def acquireSession: Either[BspError, BspSession] = session.synchronized {
    session match {
      case Some(currentSession) if currentSession.isAlive =>
        Right(currentSession)

      case Some(deadSession) =>
        openSession

      case None => openSession
    }
  }

  private def openSession: Either[BspError, BspSession] = {
    val sessionResult = prepareSession(base, executionSettings)

    sessionResult match {
      case Left(error) =>
        log.warn("bsp connection failed", error)
      case Right(newSession) =>
        session = Some(newSession)
    }

    sessionResult
  }

  def closeSession(): Future[Unit] = session match {
    case None => Future.successful(())
    case Some(s) =>
      val promise = Promise[Unit]
      s.shutdown()
        .whenComplete{(success,error) =>
          if (error != null) promise.failure(error)
          else promise.success(())
        }
      promise.future
  }

  def run[T, A](task: BspSessionTask[T], default: A, aggregator: NotificationAggregator[A]): BspJob[(T, A)] = {
    acquireSession match {
      case Left(error) => new FailedBspJob(error)
      case Right(currentSession) =>
        currentSession.run(task, default, aggregator)
    }
  }

  def run[T](bspSessionTask: BspSessionTask[T], notifications: NotificationCallback): BspJob[T] = {
    val callback = (a: Unit, n: BspNotification) => notifications(n)
    val job = run(bspSessionTask, (), callback)
    new NonAggregatingBspJob(job)
  }

}


object BspCommunication {

  // TODO since IntelliJ projects can correspond to multiple bsp modules, figure out how to have independent
  //      BspCommunication instances per base path
  def forProject(project: Project): BspCommunication = {
    val pm = project.getComponent(classOf[BspCommunicationComponent])
    if (pm == null) throw new IllegalStateException(s"unable to get component BspCommunication for project $project")
    else pm.communication
  }

  def forBaseDir(baseDir: String, executionSettings: BspExecutionSettings): BspCommunication = {
    val baseFile = new File(baseDir)
    if (!baseFile.isDirectory) throw new IllegalArgumentException(s"Base path for BspCommunication is not a directory: $baseDir")
    new BspCommunication(baseFile, executionSettings)
  }


  private[protocol] def prepareSession(base: File, bspExecutionSettings: BspExecutionSettings): Either[BspError, BspSession] = {

    val supportedLanguages = List("scala","java") // TODO somehow figure this out more generically?
    val capabilities = BspCapabilities(supportedLanguages, providesFileWatching = false) // TODO we can provide file watching

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
      if (bloopConfigDir.exists()) new BloopConnector(bspExecutionSettings.bloopExecutable, base, capabilities)
      else {
        // TODO need a protocol to detect generic bsp server
        new GenericConnectorSync(base, capabilities)
      }

    connector.connect(preferredMethod, tcpMethod)
  }


  private def findFreePort(port: Int): Int = {
    val port = 5001
    if (NetUtils.canConnectToSocket("localhost", port)) port
    else NetUtils.findAvailableSocketPort()
  }

}
