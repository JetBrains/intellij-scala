package org.jetbrains.bsp.protocol

import java.io.File
import java.net.URI
import java.nio.file._
import java.util.concurrent.TimeUnit

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.{Project, ProjectManager, ProjectManagerListener, ProjectUtil}
import com.intellij.openapi.util.Disposer
import com.intellij.util.concurrency.AppExecutorUtil
import org.jetbrains.bsp.settings.BspExecutionSettings

import scala.collection.mutable
import scala.concurrent.duration._
import scala.util.{Success, Try}

class BspCommunicationService extends Disposable {

  import BspCommunicationService.projectPath

  { // init
    val app = ApplicationManager.getApplication
    Disposer.register(app, this)

    val bus = ApplicationManager.getApplication.getMessageBus.connect()
    bus.subscribe(ProjectManager.TOPIC, MyProjectListener)
  }

  private val timeout = 10.minutes
  private val cleanerPause = 10.seconds

  private val comms = mutable.Map[URI, BspCommunication]()

  private val executorService = AppExecutorUtil.getAppScheduledExecutorService

  private val commCleaner = executorService
    .scheduleWithFixedDelay(() => closeIdleSessions(), cleanerPause.toMillis, cleanerPause.toMillis, TimeUnit.MILLISECONDS)

  private def closeIdleSessions(): Unit = {
    val now = System.currentTimeMillis()
    comms.values.foreach { comm =>
      if (comm.isIdle(now, timeout))
        comm.closeSession()
    }
  }

  private[protocol] def communicate(base: File): BspCommunication =
    comms.getOrElseUpdate(
      base.getCanonicalFile.toURI,
      {
        val comm = new BspCommunication(base, executionSettings(base))
        Disposer.register(this, comm)
        comm
      }
    )

  @deprecated("Multiple BSP servers per IDEA project are possible. use communicate(File) instead", "2020.1")
  private[protocol] def communicate(implicit project: Project): BspCommunication =
    projectPath.map(new File(_))
      .map(communicate)
      .orNull // TODO

  def listOpenComms: Iterable[URI] = comms.keys

  def isAlive(base: URI): Boolean = communicate(new File(base)).alive

  def closeCommunication(base: File): Try[Unit] =
    closeCommunication(base.getCanonicalFile.toURI)

  def closeCommunication(base: URI): Try[Unit] =
    comms.get(base)
      .toRight(new NoSuchElementException)
      .toTry
      .flatMap(_.closeSession())

  def closeAll: Try[Unit] =
    comms.values
      .map(_.closeSession())
      .foldLeft(Success(Unit): Try[Unit])(_.orElse(_))

  private def executionSettings(base: File): BspExecutionSettings =
    BspExecutionSettings.executionSettingsFor(base)

  override def dispose(): Unit = {
    comms.values.foreach(_.closeSession())
    commCleaner.cancel(true)
  }

  private object MyProjectListener extends ProjectManagerListener {

    override def projectClosed(project: Project): Unit = for {
      path <- projectPath(project)
      uri = Paths.get(path).toUri
      session <- comms.get(uri)
    } session.closeSession()
  }
}

object BspCommunicationService {

  def getInstance: BspCommunicationService =
    ServiceManager.getService(classOf[BspCommunicationService])

  private def projectPath(implicit project: Project): Option[String] =
    Option(ProjectUtil.guessProjectDir(project))
      .map(_.getCanonicalPath)
}