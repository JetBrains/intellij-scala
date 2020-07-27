package org.jetbrains.bsp.protocol

import java.io.File
import java.util.concurrent.atomic.AtomicReference

import com.google.gson.Gson
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.{Project, ProjectUtil}
import com.intellij.openapi.vfs.VfsUtil
import org.jetbrains.bsp._
import org.jetbrains.bsp.project.BspExternalSystemManager
import org.jetbrains.bsp.protocol.BspCommunication._
import org.jetbrains.bsp.protocol.BspNotifications.BspNotification
import org.jetbrains.bsp.protocol.session.BspServerConnector._
import org.jetbrains.bsp.protocol.session.BspSession._
import org.jetbrains.bsp.protocol.session._
import org.jetbrains.bsp.protocol.session.jobs.BspSessionJob
import org.jetbrains.bsp.settings.BspProjectSettings.BspServerConfig
import org.jetbrains.bsp.settings.{BspExecutionSettings, BspProjectSettings, BspSettings}
import org.jetbrains.plugins.scala.build.BuildReporter

import scala.collection.JavaConverters._
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Try

class BspCommunication private[protocol](base: File, config: BspServerConfig) extends Disposable {

  private val log = Logger.getInstance(classOf[BspCommunication])

  private val session: AtomicReference[Option[BspSession]] = new AtomicReference[Option[BspSession]](None)

  val exitCommands: List[List[String]] = {
    val workspace = new File(base.getAbsolutePath)
    val files = BspConnectionConfig.workspaceBspConfigs(workspace)
    val argvExitCommands = files.flatMap { file =>
      val bspConnectionDetails = BspExternalSystemManager.parseAsMap(file._1)
      bspConnectionDetails.get(argvExit).flatMap{comand =>
        Try {comand.asInstanceOf[java.util.List[String]]}.toOption.map(_.asScala.toList)
      }
    }
    argvExitCommands
  }
  private def acquireSessionAndRun(job: BspSessionJob[_,_])(implicit reporter: BuildReporter):
  Either[BspError, BspSession] = session.synchronized {
    session.get() match {
      case Some(currentSession) =>
        if (currentSession.isAlive) Right(currentSession)
        else openSession(job)

      case None =>
        openSession(job)
    }
  }

  private def openSession(job: BspSessionJob[_,_])(implicit reporter: BuildReporter): Either[BspError, BspSession] = {
    val sessionBuilder = prepareSession(base, config)

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

  /** Close this session. This method may block on I/O. */
  private[bsp] def closeSession(): Future[Unit] = session.get() match {
    case None => Future.successful(())
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

    acquireSessionAndRun(job) match {
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

  val argvExit = "argvExit"

  def forWorkspace(baseDir: File, config: BspServerConfig): BspCommunication = {
    if (!baseDir.isDirectory)
      throw new IllegalArgumentException(s"Base path for BspCommunication is not a directory: $baseDir")
    else
      BspCommunicationService.getInstance.communicate(baseDir, config)
  }

  def forWorkspace(baseDir: File, project: Project): BspCommunication = {
    val bspSettings = BspUtil.bspSettings(project).getLinkedProjectSettings(baseDir.getCanonicalPath)
    val config = bspSettings.serverConfig
    forWorkspace(baseDir, config)
  }


  private def prepareSession(base: File, config: BspServerConfig)(implicit reporter: BuildReporter): Either[BspError, Builder] = {

    // TODO supported languages should be extendable
    val supportedLanguages = List("scala","java")
    val capabilities = BspCapabilities(supportedLanguages)
    val compilerOutputDir = BspUtil.compilerOutputDirFromConfig(base)
      .getOrElse(new File(base, "out"))
    val bloopEnabled = BspUtil.bloopConfigDir(base).isDefined

    val connector: Either[BspError, BspServerConnector] = config match {

      case BspProjectSettings.AutoConfig =>
        // only use workspace configs for autodetection, system configs might not be applicable
        val connectionDetails = BspConnectionConfig.workspaceBspConfigs(base)
        val configuredMethods = connectionDetails.map(_._2).map(ProcessBsp)
        if (connectionDetails.nonEmpty)
          Right(new GenericConnector(base, compilerOutputDir, capabilities, configuredMethods))
        else if (bloopEnabled)
          Right(new BloopLauncherConnector(base, compilerOutputDir, capabilities))
        else
          Left(BspErrorMessage(s"Unable to automatically determine BSP connection configuration in $base"))

      case BspProjectSettings.BloopConfig =>
        if (bloopEnabled)
          Right(new BloopLauncherConnector(base, compilerOutputDir, capabilities))
        else
          Left(BspErrorMessage(s"Bloop is not configured for BSP workspace in $base"))

      case BspProjectSettings.BspConfigFile(path) =>
        BspConnectionConfig.readConnectionFile(path.toFile)(new Gson)
          .map { details =>
            val method = ProcessBsp(details)
            new GenericConnector(base, compilerOutputDir, capabilities, List(method))
          }.toEither.left
          .map(cause => BspConnectionError(s"Unable to read BSP connection file at $path", cause))
    }

    connector.flatMap(_.connect(reporter))
  }

}
