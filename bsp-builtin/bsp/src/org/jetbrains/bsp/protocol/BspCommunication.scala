package org.jetbrains.bsp.protocol

import com.google.gson.Gson
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.{ProgressIndicator, ProgressManager}
import com.intellij.openapi.project.{Project, ProjectUtil}
import com.intellij.openapi.vfs.VfsUtil
import org.jetbrains.bsp._
import org.jetbrains.bsp.project.BspExternalSystemManager
import org.jetbrains.bsp.project.importing.BspProjectOpenProcessor.isScalaCliOrMill
import org.jetbrains.bsp.project.importing.bspConfigSteps
import org.jetbrains.bsp.project.importing.bspConfigSteps.ScalaCliSetup
import org.jetbrains.bsp.project.importing.setup.{BspConfigSetup, NoConfigSetup}
import org.jetbrains.bsp.protocol.BspCommunication._
import org.jetbrains.bsp.protocol.BspNotifications.BspNotification
import org.jetbrains.bsp.protocol.session.BspServerConnector._
import org.jetbrains.bsp.protocol.session.BspSession._
import org.jetbrains.bsp.protocol.session._
import org.jetbrains.bsp.protocol.session.jobs.BspSessionJob
import org.jetbrains.bsp.settings.BspProjectSettings.BspServerConfig
import org.jetbrains.bsp.settings.{BspExecutionSettings, BspProjectSettings, BspSettings}
import org.jetbrains.plugins.scala.build.BuildReporter
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PathExt}

import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.jdk.CollectionConverters._
import scala.util.Try

class BspCommunication private[protocol](base: Path, config: BspServerConfig) extends Disposable {

  private val log = Logger.getInstance(classOf[BspCommunication])

  private val session: AtomicReference[Option[BspSession]] = new AtomicReference[Option[BspSession]](None)

  private val runningBspConfigSetup: AtomicReference[Option[BspConfigSetup]] = new AtomicReference[Option[BspConfigSetup]](None)

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
    job: BspSessionJob[_,_],
    canGenerateBspConfigFile: Boolean
  )(implicit reporter: BuildReporter): Either[BspError, BspSession] = session.synchronized {
    session.get() match {
      case Some(currentSession) =>
        if (currentSession.isAlive) Right(currentSession)
        else openSession(job, canGenerateBspConfigFile)

      case None =>
        openSession(job, canGenerateBspConfigFile)
    }
  }

  private def openSession(
    job: BspSessionJob[_,_],
    canGenerateBspConfigFile: Boolean
  )(implicit reporter: BuildReporter): Either[BspError, BspSession] = {
    val sessionBuilder = prepareSession(findProject, canGenerateBspConfigFile)

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
        
        storeConnectionFileHash()
        
        Right(newSession)
    }
  }

  private def storeConnectionFileHash(): Unit =
    for {
      project <- findProject
      settings <- BspUtil.getBspProjectSettings(project, base)
    } {
      val currentHash = BspConnectionConfig.workspaceBspConfigsHash(base)
      settings.setConnectionFileHash(currentHash)
    }

  private def prepareSession(
    project: => Option[Project],
    canGenerateBspConfigFile: Boolean
  )(implicit reporter: BuildReporter): Either[BspError, Builder] = {
    val bspConnectionFiles = BspConnectionConfig.workspaceConfigurationFiles(base)
    val bloopProject = BspUtil.bloopConfigDir(base).isDefined
    val hasBspConfigs = bspConnectionFiles.nonEmpty || bloopProject

    lazy val settings = project.flatMap(BspUtil.getBspProjectSettings(_, base))
    def generateForScalaCliOrMill: Boolean =
      isScalaCliOrMill(base) && settings.exists { s =>
        // If the connection file hash is null, it means that it's the first BSP startup in a freshly imported project,
        // and this way we don't want to regenerate the initial BSP connection file.
        s.autoRegenerateBspConfigOnServerStartup && s.getConnectionFileHash() != null
      }

    // Skip regeneration when AutoConfig is set and multiple connection files are present - in such a case the GenericConnector#connect simply picks the first
    // available connection file, so regenerating (which may create or overwrite a file) would not reliably affect which connection file is actually used.
    val hasMultipleConnectionsWithAutoConfig = config == BspProjectSettings.AutoConfig && bspConnectionFiles.size > 1

    val shouldGenerate = canGenerateBspConfigFile && !hasMultipleConnectionsWithAutoConfig && (!hasBspConfigs || generateForScalaCliOrMill)
    if (shouldGenerate) {
      val indicator = ProgressManager.getInstance().getProgressIndicator
      if (indicator != null)
        tryToGenerateBspConfig(indicator, project, hasBspConfigs, bspConnectionFiles, settings)
    }

    // TODO supported languages should be extendable
    val supportedLanguages = List("scala","java")
    val capabilities = BspCapabilities(supportedLanguages)
    val compilerOutputDir = BspUtil.compilerOutputDirFromConfig(base)
      .getOrElse(base.resolve("out"))
    val bloopEnabled = BspUtil.bloopConfigDir(base).isDefined

    def configureBloopLauncherIfJdkExists() =
      BspJdkUtil.findOrCreateBestJdkForProject(project) match {
        case Some(jdk) => Right(new BloopLauncherConnector(base, compilerOutputDir, capabilities, jdk))
        case None => Left(BspNoJdkConfiguredError)
      }

    val connector: Either[BspError, BspServerConnector] = config match {

      case BspProjectSettings.AutoConfig =>
        // only use workspace configs for auto-detection, system configs might not be applicable
        val connectionDetails = BspConnectionConfig.workspaceBspConfigs(base)
        val configuredMethods = connectionDetails.map(_._2).map(ProcessBsp)
        if (connectionDetails.nonEmpty)
          Right(new GenericConnector(base, compilerOutputDir, capabilities, configuredMethods))
        else if (bloopEnabled)
          configureBloopLauncherIfJdkExists()
        else
          Left(BspInvalidAutoConfigError(base))

      case BspProjectSettings.BloopConfig =>
        if (bloopEnabled)
          configureBloopLauncherIfJdkExists()
        else
          Left(BspErrorMessage(s"Bloop is not configured for BSP workspace in $base"))

      case BspProjectSettings.BspConfigFile(path) =>
        BspConnectionConfig.readConnectionFile(path)(new Gson)
          .map { details =>
            val method = ProcessBsp(details)
            new GenericConnector(base, compilerOutputDir, capabilities, List(method))
          }.toEither.left
          .map(cause => BspConnectionFileError(path, cause))
    }

    connector.flatMap(_.connect(reporter))
  }

  /**
   * Generates a BSP configuration file if exactly one setup choice exists and a JDK is available.
   */
  private def tryToGenerateBspConfig(
    indicator: ProgressIndicator,
    findProject: => Option[Project],
    hasBspConfigs: Boolean,
    bspConnectionFiles: Seq[Path],
    settings: => Option[BspProjectSettings]
  )(implicit reporter: BuildReporter): Unit = {
    val setupChoices = bspConfigSteps.workspaceSetupChoices(base)
    // If there is more than one setup choice or no JDK, a notification will prompt the user to run GenerateBspConfig manually.
    // See org.jetbrains.bsp.BspConnectionConfigError
    if (setupChoices.size != 1) return

    BspJdkUtil.findOrCreateBestJdkForProject(findProject).foreach { jdk =>
      val setupChoice = setupChoices.head
      val parameters = bspConfigSteps.getBuilderConfigurationParameters(jdk, base, setupChoice, considerExistingConfigs = false)
      val setup = parameters.bspConfigSetup
      if (setup == NoConfigSetup) return

      if (runningBspConfigSetup.compareAndSet(None, Some(setup))) {
        val beforeGenerationHash = BspConnectionConfig.workspaceBspConfigsHash(base)
        val showTheNotification = hasBspConfigs && settings.exists { s =>
          // If the `beforeGenerationHash` is different from the saved hash, it means that it was changed externally (e.g., by the user)
          val isHashDifferent = Option(s.getConnectionFileHash()).exists(_ != beforeGenerationHash)
          isHashDifferent || !s.bspConfigGenerated
        }

        try {
          // Scala CLI has two installation kinds that produce different connection files:
          //   - Bundled (Scala 3.5+): generates scala.json
          //   - Standalone: generates scala-cli.json
          //
          // When regenerating the BSP connection file, we must preserve the originally used installation kind to avoid
          // creating duplicate connection files (e.g., generating scala.json when scala-cli.json already exists).
          //
          // `isScalaCliWithKnownConnectionFile` determines whether we should identify the specific connection file to regenerate:
          //   - If config == BspConfigFile(path), we know the exact file path from this
          //   - If config == AutoConfig with exactly 1 connection file, we use that file's name
          //   - If config == AutoConfig and there is no existing connection file, we don't need to worry, because any generated file will work
          //
          // Notes:
          //  - If a custom-named connection file exists (e.g., custom.json), regeneration is skipped to prevent creating standard-named files alongside it.
          //    This is handled in CommandBasedBspConfigSetup.run.
          val isScalaCliWithKnownConnectionFile = setupChoice == ScalaCliSetup && (hasBspConfigs || config.is[BspProjectSettings.BspConfigFile])
          val targetConnectionFileName =
            if (isScalaCliWithKnownConnectionFile) resolveTargetConnectionFileName(bspConnectionFiles)
            else None

          setup.run(indicator, targetConnectionFileName)
        } finally {
          runningBspConfigSetup.set(None)
        }

        val afterGenerationHash = BspConnectionConfig.workspaceBspConfigsHash(base)
        connectionFileHash = afterGenerationHash

        if (showTheNotification && beforeGenerationHash != afterGenerationHash) {
          findProject.foreach { project =>
            val service = BspConnectionFileNotificationService.getInstance(project)
            service.showConfigChangedNotification(base)
          }
        }
      }
    }
  }

  /** Resolves the name of the BSP connection file that should be preserved during regeneration. */
  private def resolveTargetConnectionFileName(bspConnectionFiles: Seq[Path]): Option[String] =
    config match {
      case BspProjectSettings.BspConfigFile(path) =>
        Some(path.getFileName.toString)
      // When config = AutoConfig, `bspConnectionFiles` can be either empty or contain 1 file, because a case with AutoConfig and
      // multiple bsp connection files is prohibited. See related code in BspCommunication.prepareSession
      case BspProjectSettings.AutoConfig if bspConnectionFiles.size == 1 =>
        Some(bspConnectionFiles.head.getFileName.toString)
      case _ => None
    }

  private def findProject =
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
        FileDocumentManager.getInstance.saveAllDocuments()
        ExternalSystemUtil.refreshProjects(new ImportSpecBuilder(project, BSP.ProjectSystemId))
      }
    case _ => // ignore
  }

  /**
   * Close this session. This method may block on I/O.
   * Consider adding synchronization to this method `session.synchronized { ... }`
   */
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

  /**
   * @param canGenerateBspConfigFile whether to auto-generate missing BSP config before server start.
   *                                 Caller must remember to call [[org.jetbrains.bsp.protocol.BspCommunication.cancelConfigGeneration]] on cancellation.
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

  /** Cancels ongoing BSP configuration file generation. */
  def cancelConfigGeneration(): Unit =
    runningBspConfigSetup.getAndSet(None).foreach(_.cancel())

  override def dispose(): Unit = {
    cancelConfigGeneration()
    closeSession()
  }
}


object BspCommunication {

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
