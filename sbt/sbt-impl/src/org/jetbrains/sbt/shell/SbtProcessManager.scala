package org.jetbrains.sbt.shell

import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.execution.configurations.*
import com.intellij.execution.configurations.GeneralCommandLine.ParentEnvironmentType
import com.intellij.execution.process.{ColoredProcessHandler, KillableProcessHandler, OSProcessHandler, OSProcessUtil}
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.{ActionGroup, DefaultActionGroup}
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.{Project, ProjectManager, ProjectManagerListener}
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.encoding.EncodingProjectManager
import com.intellij.platform.eel.provider.EelProviderUtil
import com.intellij.platform.eel.provider.utils.EelPathUtils
import com.intellij.platform.eel.provider.utils.EelPathUtils.TransferTarget
import com.intellij.terminal.ui.TerminalWidget
import com.intellij.terminal.{ProcessHandlerTtyConnector, TerminalExecutionConsole, TerminalExecutionConsoleBuilder}
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.{RequiresBackgroundThread, RequiresEdt}
import com.intellij.util.messages.MessageBusConnection
import com.jediterm.core.util.TermSize
import com.sun.jna.Platform
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.scala.extensions.*
import org.jetbrains.plugins.scala.isUnitTestMode
import org.jetbrains.sbt.SbtUtil.{detectSbtVersion as _, *}
import org.jetbrains.sbt.buildinfo.BuildInfo
import org.jetbrains.sbt.process.mock.MockSbtProcessForTests
import org.jetbrains.sbt.process.options.reporting.WarningsCollectingBuildReporter
import org.jetbrains.sbt.process.options.{SbtProcessOptions, SbtProcessOptionsResolver}
import org.jetbrains.sbt.process.{SbtProcessOutputDiagnosticsCollector, SbtRunner}
import org.jetbrains.sbt.project.SbtExternalSystemManager
import org.jetbrains.sbt.project.settings.SbtExecutionSettings
import org.jetbrains.sbt.shell.SbtProcessManager.*
import org.jetbrains.sbt.shell.action.{DebugShellAction, EOFAction, StartAction, StopAction}
import org.jetbrains.sbt.shell.communication.SbtShellLifecycle.{ShellState, ShellStateEvent}
import org.jetbrains.sbt.shell.optionsWarn.SbtShellOptionsWarningService
import org.jetbrains.sbt.shell.process.utils.{SbtSettingsInjector, SbtShellJdkSelector, SbtShellRunId, SbtShellVmOptionsBuilder}
import org.jetbrains.sbt.{SbtBundle, SbtUtil, SbtVersion, SbtVersionCapabilities, SbtVersionDetector, normalizedLocalPath}

import java.io.{OutputStreamWriter, PrintWriter}
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import scala.concurrent.TimeoutException
import scala.jdk.CollectionConverters.*

/**
 * Manages the sbt shell process instance for the project.
 * Instantiates an sbt instance when initially requested.
 */
//noinspection ApiStatus,UnstableApiUsage
@Service(Array(Service.Level.PROJECT))
final class SbtProcessManager(project: Project) extends Disposable {

  private val messageBus: MessageBusConnection = project.getMessageBus.connect
  messageBus.subscribe(ProjectManager.TOPIC, new ProjectManagerListener() {
    override def projectClosing(p: Project): Unit = {
      if (project == p)
        SbtProcessManager.instanceIfCreated(project).foreach(_.dispose())
    }
  })

  import SbtProcessManager.ProcessData

  private val log = Logger.getInstance(getClass)

  @volatile private var processData: Option[ProcessData] = None
  private var processDestroyInProgress: Option[ProcessData] = None
  private val processDataMutex = new Object
  private val processStartupMutex = new Object

  private val eelDescriptor = EelProviderUtil.getEelDescriptor(project)

  private case class ProcessDestroyRequest(
    pd: ProcessData,
    shell: SbtShellCommunication,
    shouldEmitProcessTerminated: Boolean,
  )

  /**
   * @return A tuple containing:
   *         - `OSProcessHandler`: the configured process handler instance for the sbt shell.
   *         - `Option[RemoteConnection]`: debug connection information if the shell debug mode is enabled; otherwise, None.
   *         - `SbtVersion`: detected sbt version used in the project.
   *         - `Boolean`: A flag indicating whether the "new sbt shell" is being used.
   */
  private def createShellProcessHandler: (OSProcessHandler, Option[RemoteConnection], SbtVersion, Boolean) = {
    log.debug("createShellProcessHandler")
    val workingDirPath = getWorkingDirPath(project)
    val workingDir = Path.of(workingDirPath)

    val sbtSettings = getSbtSettings(workingDirPath)
    lazy val launcher = EelPathUtils.transferLocalContentToRemote(
      SbtUtil.getLauncherJar(sbtSettings),
      TransferTarget.Temporary(eelDescriptor)
    )

    // Use sbtStructureVersion as an approximation of compatible IDEA versions.
    val runId: SbtShellRunId =
      SbtShellRunId(BuildInfo.sbtStructureVersion)

    saveAllDocumentsToDisk()

    val projectSbtVersion = SbtUtil.detectSbtVersion(workingDir, launcher)
    val addPluginCommandSupported = SbtVersionCapabilities.isAddPluginCommandSupported(projectSbtVersion)
    log.debug(s"projectSbtVersion = $projectSbtVersion")
    log.debug(s"addPluginCommandSupported = $addPluginCommandSupported")

    // Allow using the "new shell" only with sbt >= 1.4, because starting from this version, JLine 3 is utilized within the "shell" command.
    // This provides better completion support and is used for detecting when the shell is ready
    // (see org.jetbrains.sbt.shell.communication.SbtShellOutputRecognizer.isPromptReady).
    val useNewShell = Registry.is("sbt.new.shell") && projectSbtVersion >= SbtVersion.apply("1.4")

    val optionsReporter = new WarningsCollectingBuildReporter
    val shellSbtProcessOptions: SbtProcessOptions =
      SbtProcessOptionsResolver.resolveSbtOptionsForShell(
        workingDir,
        sbtSettings.sbtOptions.options,
        EnvironmentVariablesData.create(sbtSettings.userSetEnvironment.asJava, sbtSettings.passParentEnvironment),
        malformedSbtOptionsFromSettings = sbtSettings.sbtOptions.malformedOptions
      )(using optionsReporter)
    SbtShellOptionsWarningService.instance(project).showWarnings(optionsReporter.collectedWarnings)

    val vmOptionsData: SbtShellVmOptionsData =
      new SbtShellVmOptionsBuilder().createVmOptions(
        sbtSettings,
        workingDir,
        addPluginCommandSupported,
        runId,
        shellSbtProcessOptions.allVmOptions,
        useNewShell = useNewShell
      )

    val vmOptions: ParametersList =
      vmOptionsData.vmOptions

    val settingsInjector = new SbtSettingsInjector(project)
    val settingsFile: Path =
      settingsInjector.getOrCreateSettingsFileWithInjectedSettings(runId, projectSbtVersion, addPluginCommandSupported, vmOptions)

    val programParams: ParametersList =
      createProgramParameters(
        settingsFile,
        shellSbtProcessOptions.sbtLauncherArgs,
        addPluginCommandSupported = addPluginCommandSupported,
        useNewShell = useNewShell
      )

    val vmExecutable: Path =
      new SbtShellJdkSelector(project).selectVmExecutableForSettings(sbtSettings)

    val commandLine: PtyCommandLine =
      createPtyCommandLine(vmExecutable, workingDir, vmOptions, launcher, programParams, sbtSettings.passParentEnvironment, sbtSettings.userSetEnvironment, useNewShell)

    // KillableProcessHandler is a handler compatible with TerminalExecutionConsole. Maybe it will change IJPL-212220
    val processHandler: KillableProcessHandler =
      if (useNewShell)
        new KillableProcessHandler(commandLine)
      else
        new ColoredProcessHandler(commandLine)

    processHandler.setShouldKillProcessSoftly(true)
    SbtProcessOutputDiagnosticsCollector.collectProcessOutputFrom(
      processHandler,
      processTitle = s"SBT shell process output (${if (useNewShell) "new-shell" else "old-shell"})",
    )

    (processHandler, vmOptionsData.debugConnection, projectSbtVersion, useNewShell)
  }

  /**
   * By saving all documents we ensure that edits in `project/build.properties` are saved to disk.<br>
   * Otherwise, user might change `sbt.version`, reload the project, and there will be a warning in sbt shell:
   * ```
   * [warn] sbt version mismatch, using: 1.9.1, in build.properties: "1.9.2", use 'reboot' to use the new value.
   * ```
   *
   * Without this call the `saveAllDocuments` would be called anyway after the sbt process is started.
   * But it would be too late already, because the warning would be already generated by sbt.
   */
  private def saveAllDocumentsToDisk(): Unit = {
    if (!isUnitTestMode) {
      invokeAndWait {
        inWriteAction {
          FileDocumentManager.getInstance().saveAllDocuments()
        }
      }
    }
  }

  private def createProgramParameters(
    settingsFile: Path,
    sbtLauncherArgs: Seq[String],
    addPluginCommandSupported: Boolean,
    useNewShell: Boolean,
  ): ParametersList = {
    val programParams = new ParametersList

    if (addPluginCommandSupported) {
      val settingsPath = settingsFile.normalizedLocalPath
      programParams.add(s"early(addPluginSbtFile=\"\"\"$settingsPath\"\"\")")
    }

    val commands = if (useNewShell) "shell" else "idea-shell"

    programParams.add(commands)
    programParams.addAll(sbtLauncherArgs *)

    programParams
  }

  private def getSbtSettings(dir: String): SbtExecutionSettings =
    SbtExternalSystemManager.executionSettingsFor(project, dir)

  /**
   * Because the regular GeneralCommandLine process doesn't mesh well with JLine on Windows, use a
   * Pseudo-Terminal based command line.
   */
  private def createPtyCommandLine(
    vmExecutable: Path,
    workingDir: Path,
    vmParams: ParametersList,
    launcher: Path,
    programParams: ParametersList,
    passParentEnvironment: Boolean,
    environment: Map[String, String],
    withNewShell: Boolean
  ): PtyCommandLine = {
    val pty = new PtyCommandLine()
    pty.withExePath(vmExecutable.toString)
    pty.withWorkingDirectory(workingDir)
    pty.withEnvironment(environment.asJava)

    if isUnitTestMode && SystemInfo.isWindows then
      pty.withEnvironment(SbtRunner.defaultCoursierDirectoriesAsEnvVariables().asJava)

    pty.addParameters(vmParams.getList)

    if (MockSbtProcessForTests.isEnabled(project)) {
      pty.addParameters(MockSbtProcessForTests.mockMainClassCommandLineTailForSbtShell(project, withNewShell) *)
    } else {
      pty.addParameters("-jar", launcher.normalizedLocalPath)
      pty.addParameters(programParams.getList)
    }

    val parentEnvironmentType = if (passParentEnvironment) ParentEnvironmentType.CONSOLE else ParentEnvironmentType.NONE
    pty.withParentEnvironmentType(parentEnvironmentType)

    // The console mode needs to be disabled when TerminalExecution is used (see com.intellij.execution.process.LocalPtyOptions.Builder.consoleMode)
    if (withNewShell) {
      pty.withConsoleMode(false)
    }

    /*
     Setting an initial PTY window size for the sbt shell pty process.

     Initially (in the "old shell"), this was only required on Windows since the terminal defaults to 80 columns,
     causing line wrapping and breaking highlighting. However, with the new shell enabled,
     it is required for both Windows and Unix (due to the use of jline3 in the built-in "shell" command since sbt 1.4).
     This is because if the shell window is minimized at startup, the terminal may report a 0×0 size.
     As a result, ">..." (see `org.jline.reader.impl.LineReaderImpl#redisplay`) will be displayed instead of the shell prompt,
     which breaks the shell ready mechanism.

     Behavior:
       - New shell: applies on both Unix and Windows.
       - Old shell: applies on Windows only.

     Dynamic window size adjustments can be done in the custom TTY connector (see [[createTerminalConsole]]).
     */
    val requireResizeForOldShell = !isUnitTestMode && Platform.isWindows
    val shouldPatchInitialSize = requireResizeForOldShell || withNewShell

    if (shouldPatchInitialSize) {
      pty.withInitialColumns(2000)
      pty.withInitialRows(100)
    }

    pty
  }

  /**
   * Send a single CTRL+C/SIGINT signal
   * If a task is already running, it terminates the currently running task.
   * If no task is running, this request might result in terminating the sbt shell.
   */
  def requestTaskCancellation(): Unit = {
    processData match {
      case Some(pd) =>
        log.debug("requestTaskCancellation start: terminateProcessGracefully...")

        OSProcessUtil.terminateProcessGracefully(pd.processHandler.getProcess)

        log.debug("requestTaskCancellation finish")
      case _ =>
        log.debug("requestTaskCancellation: skipping (no process data available)")
    }
  }

  /** Asynchronously initializes SbtShellRunner with sbt process, console UI, and optionally opens sbt shell window. */
  def initAndRunAsync(activateSbtShellToolWindowOnStartup: Boolean = currentProcessToolWindowActivationOnStartup): Unit = {
    log.debug("initAndRunAsync start...")

    executeOnPooledThread {
      acquireShellProcessHandler(activateSbtShellToolWindowOnStartup)
      if (activateSbtShellToolWindowOnStartup) {
        SbtShellRunner.openShell(focus = true, project)
      }

      log.debug("initAndRunAsync finish")
    }
  }

  private def currentProcessToolWindowActivationOnStartup: Boolean =
    processDataMutex.synchronized {
      processData match {
        case Some(pd) if isAlive(pd) =>
          pd.activateSbtShellToolWindowOnStartup
        case _ => true
      }
    }

  private def updateProcessData(activateSbtShellToolWindowOnStartup: Boolean): ProcessData = {
    log.debug("updateProcessData start...")

    val _processData = createProcessData(activateSbtShellToolWindowOnStartup)
    processDataMutex.synchronized {
      processData = Some(_processData)

      val communication = SbtShellCommunication.forProject(project)
      communication.initCommunication(_processData.processHandler)
    }

    _processData match {
      case pd: AbstractConsoleProcessData =>
        pd.runner.initAndRun()
      case pd: TerminalConsoleProcessData =>
        initTerminalConsole(pd)
        ConsoleViewsRegistry.set(project, pd.console)
        SbtShellRunner.openShellOnStartup(
          activateSbtShellToolWindowOnStartup,
          focus = false,
          openShell = focus => SbtShellRunner.openShell(focus, project),
        )
        installTerminalWarningsHost(pd)
    }

    log.debug(s"updateProcessData finish (sbtVersion=${_processData.sbtVersion}, isNewShell=${_processData.isNewShell})")

    _processData
  }

  private def createProcessData(activateSbtShellToolWindowOnStartup: Boolean): ProcessData = {
    val (handler, debugConnection, sbtVersion, useNewShell) = createShellProcessHandler

    if (useNewShell) {
      val console = createTerminalConsole(handler)
      TerminalConsoleProcessData(handler, sbtVersion, debugConnection, console, useNewShell, activateSbtShellToolWindowOnStartup)
    } else {
      val title = project.getName
      val runner = new SbtShellRunner(project, title, debugConnection, activateSbtShellToolWindowOnStartup)
      AbstractConsoleProcessData(handler, sbtVersion, debugConnection, runner, useNewShell, activateSbtShellToolWindowOnStartup)
    }
  }

  /**
   * Create a `TerminalExecutionConsole` for the sbt shell process with a custom `ProcessHandlerTtyConnector`.
   *
   * The custom `ProcessHandlerTtyConnector` overrides the `resize` method to enforce a minimum rows in terminal.
   * If the terminal rows fall at or below the minimum value used by `org.jline.reader.impl.LineReaderImpl` (JLine 3),
   * JLine switches to a mode where output is displayed on a single line.
   * This results in the following issues:
   *   - Commands are not fully rendered, e.g., during project sync, parts of the command are replaced with "..."
   *   - If minimized to 0 rows, the prompt becomes "...>", which breaks the logic for detecting when the sbt shell is ready for input.
   *
   * To prevent these problems, the terminal size is adjusted so that the number of rows is always greater than JLine’s `MIN_ROWS`.
   */
  private def createTerminalConsole(handler: OSProcessHandler): TerminalExecutionConsole = {
    val console = TerminalExecutionConsoleBuilder(project).build()
    val ttyConnector = new ProcessHandlerTtyConnector(handler, EncodingProjectManager.getInstance(project).getDefaultCharset) {
      override def resize(termSize: TermSize): Unit = {
        val minRows = 3 // from org.jline.reader.impl.LineReaderImpl.MIN_ROWS
        val adjustedTermSize =
          if (termSize.getRows <= minRows)
            new TermSize(termSize.getColumns, minRows + 1)
          else
            termSize

        super.resize(adjustedTermSize)
      }
    }
    console.attachToProcess(handler, ttyConnector, true)
    console
  }

  /** Supply a PrintWriter that writes to the current process. */
  def usingWriter[T](f: PrintWriter => T): T = {
    val processInput = acquireShellProcessHandler().getProcessInput
    val writer = new PrintWriter(new OutputStreamWriter(processInput))
    f(writer)
  }

  /**
   * Request an sbt she'll process instance. It will be started if necessary.
   * The process handler should only be used to access the running process!
   * SbtProcessManager is solely responsible for handling the running state.
   *
   * The background thread is required because other parts of the invoked code also require it
   * (for example [[SbtExternalSystemManager.executionSettingsFor]]).
   */
  @RequiresBackgroundThread
  def acquireShellProcessHandler(activateSbtShellToolWindowOnStartup: Boolean = true): OSProcessHandler = {
    log.debug("acquireShellProcessHandler start...")
    if isUnitTestMode then
      ThreadingAssertions.assertBackgroundThread()

    existingAliveProcessData match {
      case Some(pd) if isAlive(pd) =>
        log.debug("acquireShellProcessHandler finish: reusing existing alive process handler")
        pd.processHandler
      case _ =>
        processStartupMutex.synchronized {
          waitForProcessDestroyToFinish()
          existingAliveProcessData match {
            case Some(pd) if isAlive(pd) =>
              log.debug("acquireShellProcessHandler finish: reusing existing alive process handler")
              pd.processHandler
            case _ =>
              log.debug("acquireShellProcessHandler: no alive process, creating new one...")
              val handler = updateProcessData(activateSbtShellToolWindowOnStartup).processHandler
              log.debug("acquireShellProcessHandler finish: created new process handler")
              handler
          }
        }
    }
  }

  private def existingAliveProcessData: Option[ProcessData] =
    processDataMutex.synchronized {
      if (processDestroyInProgress.nonEmpty) None
      else processData.filter(isAlive)
    }

  @TestOnly
  @Internal
  def flushConsoleOutputForTests(): Unit =
    processData match {
      case Some(pd) if isAlive(pd) =>
        invokeAndWait {
          pd.flushText()
        }
      case _ =>
        throw new Exception("Process data is not available")
    }

  /**
   * Checks whether the sbt version used by the sbt shell process matches the current sbt version.
   * Returns `false` if the sbt shell is not running.
   *
   * @see [[org.jetbrains.sbt.SbtVersionDetector.detectSbtVersion]]
   */
  def isSbtVersionOutdated: Boolean = processDataMutex.synchronized {
    processData.filter(isAlive).exists { data =>
      data.sbtVersion != SbtVersionDetector.detectSbtVersion(project)
    }
  }

  /**
   * @return Some shell runner if sbt shell is already running<br>
   *         None if sbt shell is not running
   */
  def shellRunner: Option[SbtShellRunner] = processData.collect { case x: AbstractConsoleProcessData => x.runner }
  def terminalConsole: Option[TerminalExecutionConsole] = processData.collect { case x: TerminalConsoleProcessData => x.console }
  def debugConnection: Option[RemoteConnection] = processData.flatMap(_.debugConnection)

  @RequiresBackgroundThread
  def restartProcess(): Unit = {
    doRestartProcess()
  }

  private def doRestartProcess(): Unit = processStartupMutex.synchronized {
    log.debug("restartProcess")
    destroyProcess()
    waitForProcessDestroyToFinish()
    updateProcessData(activateSbtShellToolWindowOnStartup = true)
  }

  //TODO: extract common "retry" utilities
  private def terminateProcessGracefully(process: Process): Unit = {
    def attemptTermination(): Unit = {
      try OSProcessUtil.terminateProcessGracefully(process)
      catch {
        case _: UnsupportedOperationException => process.destroy()
      }
    }

    // 1 try and 4 retries, will wait 3 seconds, 6 seconds, 9 seconds and 12 seconds between each retry
    // before finally giving up and stopping the process by force
    var tries = 5
    var success = false
    var timeout = 3L
    val backoff = 3L // Back off for additional 3 seconds before each retry.

    while (!success && tries > 0) {
      attemptTermination()
      try {
        process.onExit().get(timeout, TimeUnit.SECONDS)
        success = true
      } catch {
        case _: TimeoutException =>
          timeout += backoff
          tries -= 1
      }
    }

    if (!success) {
      process.destroyForcibly()
    }
  }

  /**
   * Perform a "hard" destroy of the sbt shell, where the emptying queue process is canceled if it is running.
   */
  def destroyProcess(): Unit =
    destroyProcess(isSoft = false)

  /**
   * Perform a "soft" destroy of the sbt shell, where the emptying queue process is not canceled, and post-restart commands are preserved.
   */
  def softDestroyProcess(): Unit =
    destroyProcess(isSoft = true)

  /**
   * @param isSoft when `true`, the after-restart commands buffer is preserved.
   *               When `false`, the soft-restart is canceled if it is running.
   */
  private def destroyProcess(isSoft: Boolean): Unit =
    prepareDestroyProcess(isSoft).foreach { request =>
      try {
        runProcessTermination(request.pd)
        finishDestroyProcess(request)
      } catch {
        case ex: Throwable =>
          clearProcessDestroyInProgress(request.pd)
          throw ex
      }
    }

  private def prepareDestroyProcess(isSoft: Boolean): Option[ProcessDestroyRequest] = processDataMutex.synchronized {
    log.debug("destroyProcess start...")

    if (processDestroyInProgress.isDefined) {
      log.debug("destroyProcess finish: destroy is already in progress")
      return None
    }

    processData match {
      case Some(pd) =>
        processDestroyInProgress = Some(pd)

        prepareDestroyProcessInner(isSoft, pd)
      case None => // nothing to do
        log.debug("destroyProcess finish: no processData")
        None
    }

  }

  private def prepareDestroyProcessInner(isSoft: Boolean, pd: ProcessData): Option[ProcessDestroyRequest] = {
    try {
      val shell = SbtShellCommunication.forProject(project)
      val shellStateBeforeDestroy = shell.currentState
      // Startup may fail before the communication layer observes a ready prompt.
      // In that case the process still needs disposal, but the shell lifecycle is already Off.
      val shouldEmitShutdownRequested = !shellStateBeforeDestroy.isShuttingDownOrOff
      // If shutdown was already requested elsewhere, only the terminal event is still needed.
      val shouldEmitProcessTerminated = !shellStateBeforeDestroy.isOff

      // For hard kill: terminate after-restart commands buffer and transition to ShuttingDown
      // For soft restart: transition to ShuttingDown
      if (shouldEmitShutdownRequested) {
        if (!isSoft)
          shell.initiateHardKill()
        else
          shell.enterShuttingDownState()
      }

      Some(ProcessDestroyRequest(pd, shell, shouldEmitProcessTerminated))
    } catch {
      case ex: Throwable =>
        clearProcessDestroyInProgressLocked(pd)
        throw ex
    }
  }

  private def runProcessTermination(pd: ProcessData): Unit = {
    val runnable: Runnable = () => terminateProcessGracefully(pd.processHandler.getProcess)
    if (isUnitTestMode)
      runnable.run()
    else
      ProgressManager.getInstance().runProcessWithProgressSynchronously(runnable, SbtBundle.message("sbt.shell.stopping.process"), false, project)
  }

  private def finishDestroyProcess(request: ProcessDestroyRequest): Unit = processDataMutex.synchronized {
    try {
      if (request.shouldEmitProcessTerminated) {
        log.trace("destroyProcess: emit ProcessTerminated...")
        request.shell.emitShellStateEvent(ShellStateEvent.ProcessTerminated)
      }
      uninstallTerminalWarningsHost(request.pd)

      processData match {
        case Some(currentPd) if currentPd eq request.pd =>
          processData = None
          log.debug("destroyProcess finish: processData cleared")
        case Some(_) =>
          log.debug("destroyProcess finish: processData already replaced")
        case None =>
          log.debug("destroyProcess finish: processData already cleared")
      }
    } finally {
      clearProcessDestroyInProgressLocked(request.pd)
    }
  }

  private def waitForProcessDestroyToFinish(): Unit = processDataMutex.synchronized {
    if (processDestroyInProgress.nonEmpty)
      log.debug("waiting for destroyProcess to finish...")
    while (processDestroyInProgress.nonEmpty) {
      processDataMutex.wait()
    }
  }

  private def clearProcessDestroyInProgress(pd: ProcessData): Unit = processDataMutex.synchronized {
    clearProcessDestroyInProgressLocked(pd)
  }

  private def clearProcessDestroyInProgressLocked(pd: ProcessData): Unit = {
    processDestroyInProgress match {
      case Some(currentPd) if currentPd eq pd =>
        processDestroyInProgress = None
        processDataMutex.notifyAll()
      case _ =>
    }
  }

  def sendSigInt(): Unit = processData.foreach(_.processHandler.destroyProcess())

  override def dispose(): Unit = {
    // Intentionally do NOT waitForProcessDestroyToFinish() here: if a destroy is already in progress,
    // destroyProcess() returns immediately and we let it complete on its owning thread.
    // dispose() runs on the EDT during project close, and the in-progress termination needs the EDT to
    // pump its modal progress, so waiting here would re-freeze the EDT and reintroduce the deadlock
    // this class is designed to avoid (see prepareDestroyProcess / runProcessTermination, SCL-25654).
    destroyProcess()
    ConsoleViewsRegistry.disposeLast(project)
  }

  /** Report if shell process is alive. Should only be used for UI/informational purposes. */
  private[shell] def isAlive: Boolean =
    processDataMutex.synchronized {
      processDestroyInProgress.isEmpty && processData.exists(isAlive)
    }

  private def isAlive(processData: ProcessData): Boolean = {
    // processData.processHandler.getProcess.isAlive // TODO: I am not sure which is the best
    !processData.processHandler.isProcessTerminated
  }

  def sbtVersionUsedDuringProcessStart: Option[SbtVersion] =
    processData.map(_.sbtVersion)

  private def createActionGroupForTerminalConsole(terminal: TerminalExecutionConsole): ActionGroup = {
    // By default, provides ScrollToTheEndAction and ClearAction
    val defaultActions = terminal.createConsoleActions()

    // The "Toggle Soft Wrap" action is not needed when using TerminalExecutionConsole,
    // as soft wrapping happens automatically when the terminal is resized.
    val startAction = new StartAction(project)
    val stopAction = new StopAction(project)
    val debugShellAction = new DebugShellAction(project, debugConnection)

    // CopyFromHistoryViewerAction/FindAction/EscapeAction automatically works within TerminalExecutionConsole
    val eofAction = new EOFAction(project)

    val allActions = List(startAction, stopAction, debugShellAction, eofAction) ++ defaultActions
    allActions.foreach { act =>
      act.registerCustomShortcutSet(act.getShortcutSet, terminal.getComponent)
    }

    val group = new DefaultActionGroup()
    group.addAll(startAction, stopAction, debugShellAction)
    group.addSeparator()
    group.addAll(defaultActions*)
    group
  }

  private def initTerminalConsole(processData: TerminalConsoleProcessData): Unit =
    executeOnPooledThread {
      processData.processHandler.startNotify()

      val actionGroup = createActionGroupForTerminalConsole(processData.console)
      SbtShellToolWindowFactory.initUi(project, actionGroup, component = processData.console.getComponent)
    }

  private def installTerminalWarningsHost(processData: TerminalConsoleProcessData): Unit =
    SbtShellOptionsWarningService.instance(project).installTerminalWidget(processData.terminalWidget)

  private def uninstallTerminalWarningsHost(processData: ProcessData): Unit =
    processData match {
      case terminalData: TerminalConsoleProcessData =>
        SbtShellOptionsWarningService.instance(project).uninstallTerminalWidget(terminalData.terminalWidget)
      case _ =>
    }

  private[shell] def isRunWithNewShell: Boolean =
    processData.exists(_.isNewShell)
}

object SbtProcessManager {

  case class SbtShellVmOptionsData(
    vmOptions: ParametersList,
    debugConnection: Option[RemoteConnection],
  )

  def forProject(project: Project): SbtProcessManager = {
    val pm = project.getService(classOf[SbtProcessManager])
    if (pm == null) throw new IllegalStateException(s"unable to get component SbtProcessManager for project $project")
    else pm
  }

  private[sbt] def instanceIfCreated(project: Project): Option[SbtProcessManager] = {
    Option(project.getServiceIfCreated(classOf[SbtProcessManager]))
  }

  private sealed trait ProcessData {
    def processHandler: OSProcessHandler
    /**
     * Version of sbt detected when launching the sbt process
     */
    def sbtVersion: SbtVersion
    def debugConnection: Option[RemoteConnection]
    def isNewShell: Boolean

    /**
     * Whether creating this sbt shell process was allowed to open or focus the sbt shell tool window.
     *
     * The primary `true` case is the foreground shell lifecycle.
     * Most commonly, the platform has already opened or activated the sbt shell tool window before this code runs,
     * for example because the user opened the tool window or pressed Start/Restart there.
     * The flag means that process startup should not suppress the shell UI's normal follow-up activation/focus behavior.
     *
     * The primary `false` case is run/debug configuration delegation, where sbt shell is started only as a background
     * execution dependency and the Run/Debug tool window should remain the active UI.
     *
     * This is stored with process data because tool window content creation can happen lazily, after the process has
     * already been created by a run configuration. At that later point the original request object is no longer on the
     * stack, so the tool-window factory needs this process-level startup intent to avoid reopening/focusing a shell
     * that was started only as a background run/debug dependency.
     */
    def activateSbtShellToolWindowOnStartup: Boolean

    @RequiresEdt
    def flushText(): Unit
  }

  private case class AbstractConsoleProcessData(
    processHandler: OSProcessHandler,
    sbtVersion: SbtVersion,
    debugConnection: Option[RemoteConnection],
    runner: SbtShellRunner,
    isNewShell: Boolean,
    activateSbtShellToolWindowOnStartup: Boolean,
  ) extends ProcessData {

    @RequiresEdt
    override def flushText(): Unit = runner.getConsoleView.flushDeferredText()
  }

  private case class TerminalConsoleProcessData(
    processHandler: OSProcessHandler,
    sbtVersion: SbtVersion,
    debugConnection: Option[RemoteConnection],
    console: TerminalExecutionConsole,
    isNewShell: Boolean,
    activateSbtShellToolWindowOnStartup: Boolean,
  ) extends ProcessData {
    // Keep the bridge object stable so install/uninstall compare the same TerminalWidget instance.
    lazy val terminalWidget: TerminalWidget = console.getTerminalWidget.asNewWidget()

    @RequiresEdt
    override def flushText(): Unit = console.flushImmediately()
  }
}
