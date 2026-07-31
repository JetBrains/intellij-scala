package org.jetbrains.bsp.protocol

import com.google.gson.Gson
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.progress.{ProgressIndicator, ProgressManager}
import com.intellij.openapi.project.{Project, ProjectUtil}
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.provider.{EelProviderUtil, LocalEelDescriptor}
import org.jetbrains.annotations.TestOnly
import org.jetbrains.bsp.*
import org.jetbrains.bsp.project.BspExternalSystemManager
import org.jetbrains.bsp.project.importing.BspProjectOpenProcessor.isScalaCliOrMill
import org.jetbrains.bsp.protocol.BspCommunication.*
import org.jetbrains.bsp.protocol.BspConfigRegeneration.RegenerationReason
import org.jetbrains.bsp.protocol.BspNotifications.BspNotification
import org.jetbrains.bsp.protocol.session.*
import org.jetbrains.bsp.protocol.session.BspServerConnector.*
import org.jetbrains.bsp.protocol.session.BspSession.*
import org.jetbrains.bsp.protocol.session.jobs.BspSessionJob
import org.jetbrains.bsp.settings.BspProjectSettings.BspServerConfig
import org.jetbrains.bsp.settings.{BspExecutionSettings, BspProjectSettings}
import org.jetbrains.plugins.scala.build.BuildReporter
import org.jetbrains.plugins.scala.extensions.PathExt
import org.jetbrains.plugins.scala.isUnitTestMode

import java.nio.file.Path
import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}
import scala.concurrent.Future
import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*
import scala.util.Try

class BspCommunication private[protocol](base: Path, initialServerConfig: BspServerConfig) extends Disposable {

  private val log = Logger.getInstance(classOf[BspCommunication])

  private val session: AtomicReference[Option[BspSession]] = new AtomicReference[Option[BspSession]](None)

  /**
   * The [[BspSession.termination]] of the session most recently closed via [[closeSession]] - it completes
   * once that session's sbt server process tree has exited. Captured by [[closeSession]] after it triggers the shutdown
   * and clears the live [[session]], so the termination can still be awaited even though [[session]] is already `None`.
   */
  private[bsp] val lastSessionTermination = new AtomicReference[Future[Unit]](Future.successful(()))

  /**
   * The [[ProgressIndicator]] active during [[prepareSession]].
   * Cancelling this indicator is the mechanism for stopping BSP connection file generation.
   */
  private val activeIndicator = new AtomicReference[Option[ProgressIndicator]](None)

  private[protocol] var connectionFileHash = BspConnectionConfig.workspaceBspConfigsHash(base)

  lazy val exitCommands: List[List[String]] = {
    val workspace = base.toCanonicalPath
    val files = BspConnectionConfig.workspaceBspConfigs(workspace)
    val argvExitCommands = files.flatMap { file =>
      val bspConnectionDetails = BspExternalSystemManager.parseAsMap(file._1)
      bspConnectionDetails.get(argvExit).flatMap{comand =>
        Try {comand.asInstanceOf[java.util.List[String]]}.toOption.map(_.asScala.toList)
      }
    }
    argvExitCommands
  }

  private def acquireSessionAndRun(
    job: BspSessionJob[?,?],
    canGenerateBspConfigFile: Boolean
  )(implicit reporter: BuildReporter): Either[BspError, BspSession] = session.synchronized {
    val indicator = Option(ProgressManager.getInstance().getProgressIndicator)
    activeIndicator.set(indicator)
    try {
      session.get() match {
        case Some(currentSession) =>
          if (currentSession.isAlive) Right(currentSession)
          else openSession(job, canGenerateBspConfigFile)

        case None =>
          openSession(job, canGenerateBspConfigFile)
      }
    } finally {
      activeIndicator.set(None)
    }
  }

  private def openSession(
    job: BspSessionJob[?,?],
    canGenerateBspConfigFile: Boolean
  )(using reporter: BuildReporter): Either[BspError, BspSession] = {
    given EelDescriptor = EelProviderUtil.getEelDescriptor(base)
    val project = findProject
    given ctx: SessionContext = SessionContext(
      project,
      bspProjectSettings = project.flatMap(BspUtil.getBspProjectSettings(_, base)),
      indicator = activeIndicator.get(),
      initialServerConfig
    )

    val configAlreadyRegenerated =
      if canGenerateBspConfigFile && shouldRegenerateBeforeStartup then
        val newHash = BspConfigRegeneration.regenerateBspConfig(base, RegenerationReason.BeforeServerStartup)
        newHash.foreach(connectionFileHash = _)
        newHash.nonEmpty
      else
        false

    val canRegenerateOnFailure = canGenerateBspConfigFile && !configAlreadyRegenerated && ctx.indicator.isDefined

    val sessionBuilder = prepareSession match {
      // If the BSP server fails to start, regenerate the BSP connection file and prepare session again
      case Left(error: BspConnectionConfigError) if canRegenerateOnFailure =>
        log.warn("BSP server failed to start, attempting to regenerate connection file", error)
        BspConfigRegeneration.regenerateBspConfig(base, RegenerationReason.ServerFailure).foreach(connectionFileHash = _)
        prepareSession
      case other => other
    }

    sessionBuilder match {
      case Left(error) =>
        val procLogMsg = BspBundle.message("bsp.protocol.connection.failed", error.getMessage)
        job.log(procLogMsg)
        log.warn("BSP connection failed", error)

        error match {
          case _: BspConnectionConfigError =>
            findProject.foreach { project =>
              val service = BspConnectionFileNotificationService.getInstance(project)
              service.showRegenerateBspConnectionFileNotification(base)
            }
          case _ =>
        }

        Left(error)
      case Right(newSessionBuilder) =>
        newSessionBuilder
          .withInitialJob(job)
          .addNotificationCallback(projectCallback)
          .withTraceLogPredicate(() => BspExecutionSettings.executionSettingsFor(base).traceBsp)
        val newSession = newSessionBuilder.create
        session.updateAndGet(_ => Option(newSession))
        
        storeConnectionFileHash
        
        Right(newSession)
    }
  }

  private def storeConnectionFileHash(using ctx: SessionContext): Unit =
    ctx.bspProjectSettings.foreach { settings =>
      val currentHash = BspConnectionConfig.workspaceBspConfigsHash(base)
      settings.connectionFileHash = currentHash
    }

  private def prepareSession(using ctx: SessionContext, reporter: BuildReporter, eelDescriptor: EelDescriptor): Either[BspError, Builder] = {
    val bloopEnabled = BspUtil.bloopConfigDir(base).isDefined

    // TODO supported languages should be extendable
    val supportedLanguages = List("scala","java")
    val capabilities = BspCapabilities(supportedLanguages)
    val compilerOutputDir = BspUtil.compilerOutputDirFromConfig(base)
      .getOrElse(base.resolve("out"))

    val connector: Either[BspError, BspServerConnector] = ctx.serverConfig match {
      case BspProjectSettings.AutoConfig =>
        // only use workspace configs for auto-detection, system configs might not be applicable
        val connectionDetails = BspConnectionConfig.workspaceBspConfigs(base)
        val configuredMethods = connectionDetails.map(_._2).map(ProcessBsp.apply)
        if (connectionDetails.nonEmpty)
          Right(new GenericConnector(base, compilerOutputDir, capabilities, configuredMethods))
        else if (bloopEnabled)
          configureBloopLauncherIfJdkExists(compilerOutputDir, capabilities)
        else
          Left(BspInvalidAutoConfigError(base))

      case BspProjectSettings.BloopConfig =>
        if (bloopEnabled)
          configureBloopLauncherIfJdkExists(compilerOutputDir, capabilities)
        else
          Left(BspErrorMessage(s"Bloop is not configured for BSP workspace in $base"))

      case BspProjectSettings.BspConfigFile(path) =>
        BspConnectionConfig.readConnectionFile(path)(using new Gson)
          .map { details =>
            val method = ProcessBsp(details)
            new GenericConnector(base, compilerOutputDir, capabilities, List(method))
          }.toEither.left
          .map(cause => BspConnectionFileError(path, cause))
    }

    connector.flatMap(_.connect(using reporter, ctx.indicator))
  }

  private def configureBloopLauncherIfJdkExists(
    compilerOutputDir: Path,
    capabilities: BspCapabilities
  )(using eelDescriptor: EelDescriptor, ctx: SessionContext): Either[BspError, BloopLauncherConnectorBase] =
    BspJdkUtil.findOrCreateBestJdkForProject(ctx.project, eelDescriptor) match {
      case Some(jdk) =>
        val connector =
          if eelDescriptor == LocalEelDescriptor.INSTANCE then
            BloopLocalLauncherConnector(base, compilerOutputDir, capabilities, jdk)
          else
            BloopRemoteLauncherConnector(base, compilerOutputDir, capabilities, jdk, eelDescriptor)
        Right(connector)
      case None => Left(BspNoJdkConfiguredError)
    }

  /**
   * Checks whether the BSP connection file should be regenerated before the server starts.
   * Returns `false` for freshly imported projects (where `connectionFileHash` is null) to avoid overwriting the initial connection file.
   * Only applicable to Scala CLI and Mill projects.
   */
  private def shouldRegenerateBeforeStartup(using ctx: SessionContext): Boolean = {
    val isSettingEnabled =
      ctx.bspProjectSettings.exists: settings =>
        settings.autoRegenerateBspConfigOnServerStartup && settings.connectionFileHash != null

    isSettingEnabled && isScalaCliOrMill(base)
  }

  private def findProject: Option[Project] =
    for {
      vfsPath <- Option(VfsUtil.findFile(base, false))
      project <- Option(ProjectUtil.guessProjectForFile(vfsPath))
    } yield project

  private val projectCallback: NotificationCallback = {
    case BspNotifications.DidChangeBuildTarget(didChange) =>
      for {
        project <- findProject
        _ <- BspUtil.getBspProjectSettings(project, base)
      } {
        if isUnitTestMode then
          projectCallbackInvocationCount.incrementAndGet()

        log.info("[BspCommunication] Got DidChangeBuildTarget notification. Project refresh is triggered")
        ExternalSystemUtil.refreshProjects(new ImportSpecBuilder(project, BSP.ProjectSystemId))
      }
    case _ => // ignore
  }

  /** Number of times [[projectCallback]] has triggered a reload. */
  @TestOnly
  private[protocol] val projectCallbackInvocationCount = new AtomicInteger(0)
  
  /**
   * Close this session. This method may block on I/O.
   * Consider adding synchronization to this method `session.synchronized { ... }`
   */
  private[bsp] def closeSession(): Future[Unit] = session.get() match {
    case None => Future.successful(())
    case Some(s) =>
      session.set(None)
      val shutdownFuture = s.shutdown()
      Option(s.termination.get()).foreach(lastSessionTermination.set)
      shutdownFuture
  }

  private[protocol] def isIdle(now: Long, timeout: Duration) = session.get() match {
    case None => false
    case Some(s) =>
      s.isAlive && (now - s.getLastActivity >  timeout.toMillis)
  }

  def alive: Boolean = session.get().exists(_.isAlive)

  /**
   * Push a notification into the BSP session as if the BSP server had sent it.
   *
   * This is done to simplify the tests and avoid depending on a specific BSP server implementation whether
   * it sends particular notifications after certain actions or not. The goal in tests is to verify that a given notification
   * triggers the expected actions in IntelliJ.
   */
  @TestOnly
  private[protocol] def simulateServerNotificationForTest(notification: BspNotification): Unit =
    session.get() match {
      case Some(s) => s.notifications(notification)
      case None    => throw new IllegalStateException(s"No live BSP session for workspace $base")
    }

  /**
   * @param canGenerateBspConfigFile whether to auto-generate missing BSP config before server startup.
   *                                 Caller must remember to call [[cancelSessionCreation]] on cancellation.
   */
  def run[T, A](task: BspSessionTask[T],
                default: A,
                aggregator: NotificationAggregator[A],
                processLogger: ProcessLogger,
                canGenerateBspConfigFile: Boolean
               )
               (implicit reporter: BuildReporter): BspJob[(T, A)] = {
    val job = jobs.create(task, default, aggregator, processLogger)

    acquireSessionAndRun(job, canGenerateBspConfigFile) match {
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
    val job = run(bspSessionTask, (), callback, processLogger, canGenerateBspConfigFile = false)
    new NonAggregatingBspJob(job)
  }

  /**
   * Cancels the ongoing session creation, including BSP config generation.
   * Cancellation works by cancelling the [[ProgressIndicator]] captured in [[prepareSession]].
   */
  def cancelSessionCreation(): Unit =
    activeIndicator.getAndSet(None).foreach(_.cancel())

  override def dispose(): Unit = {
    cancelSessionCreation()
    closeSession()
  }
}


object BspCommunication {

  private[protocol] case class SessionContext(
    project: Option[Project],
    bspProjectSettings: Option[BspProjectSettings],
    indicator: Option[ProgressIndicator],
    initialServerConfig: BspServerConfig
  ) {
    def serverConfig: BspServerConfig =
      bspProjectSettings.map(_.serverConfig).getOrElse(initialServerConfig)
  }

  val argvExit = "argvExit"

  def forWorkspace(baseDir: Path, config: BspServerConfig): BspCommunication = {
    if (!baseDir.isDirectory)
      throw new IllegalArgumentException(s"Base path for BspCommunication is not a directory: $baseDir")
    else
      BspCommunicationService.getInstance.communicate(baseDir, config)
  }

  def forWorkspace(baseDir: Path, project: Project): BspCommunication = {
    val bspSettings = BspUtil.bspSettings(project).getLinkedProjectSettings(baseDir.toCanonicalPath.toString)
    val config = bspSettings.serverConfig
    forWorkspace(baseDir, config)
  }
}
