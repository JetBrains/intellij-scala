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
import org.jetbrains.bsp.settings.BspProjectSettings.BspServerConfig
import org.jetbrains.bsp.project.BspExternalSystemManager
import org.jetbrains.bsp.settings.BspExecutionSettings
import org.jetbrains.plugins.scala.util.UnloadAwareDisposable

import scala.collection.mutable
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Success, Try}

class BspCommunicationService extends Disposable {

  import BspCommunicationService.projectPath

  { // init
    Disposer.register(UnloadAwareDisposable.scalaPluginDisposable, this)

    val bus = ApplicationManager.getApplication.getMessageBus.connect(this)
    bus.subscribe(ProjectManager.TOPIC, MyProjectListener)
  }

  private val timeout = 10.minutes
  private val cleanerPause = 10.seconds

  private val comms = mutable.Map[(URI, BspServerConfig), BspCommunication]()

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

  private[protocol] def communicate(base: File, config: BspServerConfig): BspCommunication =
    comms.getOrElseUpdate(
      (base.getCanonicalFile.toURI, config),
      {
        val comm = new BspCommunication(base, config)
        Disposer.register(this, comm)
        comm
      }
    )

  def listOpenComms: Iterable[(URI, BspServerConfig)] = comms.keys

  def isAlive(base: URI, config: BspServerConfig): Boolean =
    comms.get((base,config)).exists(_.alive)

  /** Close BSP connection if there is an open one associated with `base`. */
  def closeCommunication(base: URI, config: BspServerConfig): Future[Unit] = {
    val tryComm = comms.get(base, config)
      .toRight(new NoSuchElementException)
      .toTry

    Future.fromTry(tryComm)
      .flatMap(_.closeSession())(ExecutionContext.global)
  }

  def exitCommands(base: URI, config: BspServerConfig): Try[List[List[String]]] = {
    comms.get(base, config)
      .toRight(new NoSuchElementException)
      .toTry
      .map(_.exitCommands)
  }

  def closeAll: Future[Unit] = {
    import ExecutionContext.Implicits.global
    Future.traverse(comms.values)(_.closeSession()).map(_ => ())
  }

  override def dispose(): Unit = {
    comms.values.foreach(_.closeSession())
    commCleaner.cancel(true)
  }

  private object MyProjectListener extends ProjectManagerListener {
    override def projectClosed(project: Project): Unit = for {
      path <- projectPath(project)
      uri = Paths.get(path).toUri
      session <- comms.view.filterKeys(_._1 == uri).values
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
