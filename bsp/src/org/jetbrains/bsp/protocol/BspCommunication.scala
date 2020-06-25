package org.jetbrains.bsp.protocol

import java.io.File
import java.util.concurrent.atomic.AtomicReference

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.{Project, ProjectUtil}
import com.intellij.openapi.vfs.VfsUtil
import org.jetbrains.bsp._
import org.jetbrains.bsp.protocol.BspCommunication._
import org.jetbrains.bsp.protocol.BspNotifications.BspNotification
import org.jetbrains.bsp.protocol.session.BspServerConnector._
import org.jetbrains.bsp.protocol.session.BspSession._
import org.jetbrains.bsp.protocol.session._
import org.jetbrains.bsp.protocol.session.jobs.BspSessionJob
import org.jetbrains.bsp.settings.{BspExecutionSettings, BspProjectSettings, BspSettings}
import org.jetbrains.plugins.scala.build.BuildReporter

import scala.concurrent.duration._
import scala.util.{Success, Try}


class BspCommunication private[protocol](base: File) extends Disposable {

  private val log = Logger.getInstance(classOf[BspCommunication])

  private val session: AtomicReference[Option[BspSession]] = new AtomicReference[Option[BspSession]](None)

  private def acquireSessionAndRun(job: BspSessionJob[_,_], reporter: BuildReporter):
  Either[BspError, BspSession] = session.synchronized {
    session.get() match {
      case Some(currentSession) =>
        if (currentSession.isAlive) Right(currentSession)
        else openSession(job, reporter)

      case None =>
        openSession(job, reporter)
    }
  }

  private def openSession(job: BspSessionJob[_,_], reporter: BuildReporter): Either[BspError, BspSession] = {
    val sessionBuilder = prepareSession(base, reporter)

    sessionBuilder match {
      case Left(error) =>
        val procLogMsg = BspBundle.message("bsp.protocol.connection.failed", error.getMessage)
        job.log(procLogMsg)
        log.warn("BSP connection failed", error)
        Left(error)
      case Right(newSessionBuilder) =>
        newSessionBuilder
          .withInitialJob(job)
          .addNotificationCallback(projectCallback)
          .withTraceLogPredicate(() => BspExecutionSettings.executionSettingsFor(base).traceBsp)
        val newSession = newSessionBuilder.create
        session.updateAndGet(_ => Option(newSession))
        Right(newSession)
    }
  }

  private def findProject: Option[Project] =
    for {
      vfsPath <- Option(VfsUtil.findFileByIoFile(base, false))
      project <- Option(ProjectUtil.guessProjectForFile(vfsPath))
    } yield project

  private def bspSettings(project: Project): Option[BspProjectSettings] =
    Option(BspSettings.getInstance(project).getLinkedProjectSettings(base.getPath))

  private val projectCallback: NotificationCallback = {
    case BspNotifications.DidChangeBuildTarget(didChange) =>
      for {
        project <- findProject
        settings <- bspSettings(project)
      } {
        FileDocumentManager.getInstance.saveAllDocuments()
        ExternalSystemUtil.refreshProjects(new ImportSpecBuilder(project, BSP.ProjectSystemId))
      }
    case _ => // ignore
  }

  private[bsp] def closeSession(): Try[Unit] = session.get() match {
    case None => Success(())
    case Some(s) =>
      session.set(None)
      s.shutdown()
  }


  private[protocol] def isIdle(now: Long, timeout: Duration) = session.get() match {
    case None => false
    case Some(s) =>
      s.isAlive && (now - s.getLastActivity >  timeout.toMillis)
  }

  def alive: Boolean = session.get().exists(_.isAlive)

  def run[T, A](task: BspSessionTask[T],
                default: A,
                aggregator: NotificationAggregator[A],
                processLogger: ProcessLogger
               )
               (implicit reporter: BuildReporter): BspJob[(T, A)] = {
    val job = jobs.create(task, default, aggregator, processLogger)

    acquireSessionAndRun(job, reporter) match {
      case Left(error) => new FailedBspJob(error)
      case Right(currentSession) =>
        currentSession.run(job)
    }
  }

  def run[T](bspSessionTask: BspSessionTask[T],
             notifications: NotificationCallback,
             processLogger: ProcessLogger)
            (implicit reporter: BuildReporter): BspJob[T] = {
    val callback = (_: Unit, n: BspNotification) => notifications(n)
    val job = run(bspSessionTask, (), callback, processLogger)
    new NonAggregatingBspJob(job)
  }

  override def dispose(): Unit = {
    closeSession()
  }
}


object BspCommunication {

  def forWorkspace(baseDir: File): BspCommunication = {
    if (!baseDir.isDirectory)
      throw new IllegalArgumentException(s"Base path for BspCommunication is not a directory: $baseDir")
    else
      BspCommunicationService.getInstance.communicate(baseDir)
  }


  private def prepareSession(base: File, reporter: BuildReporter): Either[BspError, Builder] = {

    val supportedLanguages = List("scala","java") // TODO somehow figure this out more generically?
    val capabilities = BspCapabilities(supportedLanguages)
    val connectionDetails = BspConnectionConfig.allBspConfigs(base)
    val configuredMethods = connectionDetails.map(ProcessBsp)

    val compilerOutputDir = BspUtil.compilerOutputDirFromConfig(base)
      .getOrElse(new File(base, "out"))

    // TODO user dialog when multiple valid connectors exist: https://youtrack.jetbrains.com/issue/SCL-14880
    val connector =
      if (connectionDetails.nonEmpty)
        new GenericConnector(base, compilerOutputDir, capabilities, configuredMethods)
      else if (BspUtil.bloopConfigDir(base).isDefined)
        new BloopLauncherConnector(
          base,
          compilerOutputDir,
          capabilities
        )
      else new DummyConnector(base.toURI)

    connector.connect(reporter)
  }

}
