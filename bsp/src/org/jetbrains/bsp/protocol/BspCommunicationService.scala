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


class BspCommunicationService extends Disposable {

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

  def communicate(base: File): BspCommunication =
    comms.getOrElseUpdate(
      base.getCanonicalFile.toURI,
      {
        val comm = new BspCommunication(base, executionSettings(base))
        Disposer.register(this, comm)
        comm
      }
    )

  private def executionSettings(base: File): BspExecutionSettings =
    BspExecutionSettings.executionSettingsFor(base)

  override def dispose(): Unit = {
    comms.values.foreach(_.closeSession())
    commCleaner.cancel(true)
  }

  private object MyProjectListener extends ProjectManagerListener {
    override def projectClosed(project: Project): Unit = {
      val projectDir = ProjectUtil.guessProjectDir(project)
      val uri = Paths.get(projectDir.getCanonicalPath).toUri
      comms.get(uri).foreach(_.closeSession())
    }
  }
}

object BspCommunicationService {
  def getInstance: BspCommunicationService =
    ServiceManager.getService(classOf[BspCommunicationService])
}