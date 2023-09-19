package org.jetbrains.sbt.shell

import com.intellij.debugger.engine.DebuggerUtils
import com.intellij.execution.configurations.GeneralCommandLine.ParentEnvironmentType
import com.intellij.execution.configurations._
import com.intellij.execution.process.{ColoredProcessHandler, OSProcessUtil}
import com.intellij.notification.{Notification, NotificationAction, NotificationType}
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.options.ex.SingleConfigurableEditor
import com.intellij.openapi.options.newEditor.SettingsDialog
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.{Project, ProjectManager, ProjectManagerListener, ProjectUtil}
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable
import com.intellij.openapi.ui.DialogWrapper.DialogStyle
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.messages.MessageBusConnection
import com.pty4j.unix.UnixPtyProcess
import com.pty4j.{PtyProcess, WinSize}
import org.jetbrains.annotations.NonNls
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.project.Version
import org.jetbrains.plugins.scala.project.external.{JdkByName, SdkUtils}
import org.jetbrains.plugins.scala.settings.CompilerIndicesSettings
import org.jetbrains.plugins.scala.util.ScalaNotificationGroups
import org.jetbrains.sbt.SbtUtil._
import org.jetbrains.sbt.buildinfo.BuildInfo
import org.jetbrains.sbt.project.settings.SbtExecutionSettings
import org.jetbrains.sbt.project.structure.SbtOption._
import org.jetbrains.sbt.project.{SbtExternalSystemManager, SbtProjectResolver, SbtProjectSystem}
import org.jetbrains.sbt.shell.SbtProcessManager._
import org.jetbrains.sbt.{JvmMemorySize, Sbt, SbtBundle, SbtCompilationSupervisorPort, SbtUtil}

import java.io.{File, IOException, OutputStreamWriter, PrintWriter}
import java.util.concurrent.TimeUnit
import scala.concurrent.TimeoutException
import scala.jdk.CollectionConverters._

/**
 * Manages the sbt shell process instance for the project.
 * Instantiates an sbt instance when initially requested.
 */
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

  @NonNls private def repoPath: String = normalizePath(getRepoDir)

  @NonNls private def pluginResolverSetting: String =
    raw"""resolvers += Resolver.file("Scala Plugin Bundled Repository",file(raw"$repoPath"))(Resolver.mavenStylePatterns)"""

  /** Plugins injected into user's global sbt build. */
  // TODO add configurable plugins somewhere for users and via API; factor this stuff out
  private def injectedPlugins(sbtMajorVersion: String): Seq[String] =
    sbtStructurePlugin(sbtMajorVersion)

  // this *might* get messy if multiple IDEA projects start messing with the global settings.
  // but we should be fine since it is written before every sbt boot
  private def sbtStructurePlugin(sbtMajorVersion: String): Seq[String] = {
    val sbtStructureVersion = BuildInfo.sbtStructureVersion
    val sbtIdeaShellVersion = BuildInfo.sbtIdeaShellVersion

    val compilerIndicesEnabled = CompilerIndicesSettings(project).isBytecodeIndexingActive
    val compilerIndicesPlugin  = compilerIndicesEnabled.seq {
      val pluginVersion = BuildInfo.sbtIdeaCompilerIndicesVersion
      s"""addSbtPlugin("org.jetbrains.scala" % "sbt-idea-compiler-indices" % "$pluginVersion")"""
    }


    sbtMajorVersion match {
      case "0.12" => Seq.empty // 0.12 doesn't support AutoPlugins
      case _ => Seq(
        s"""addSbtPlugin("org.jetbrains.scala" % "sbt-structure-extractor" % "$sbtStructureVersion")""",
        s"""addSbtPlugin("org.jetbrains.scala" % "sbt-idea-shell" % "$sbtIdeaShellVersion")""",
      ) ++ compilerIndicesPlugin // works for 0.13.5+, for older versions it will be ignored
    }
  }

  private def createShellProcessHandler(): (ColoredProcessHandler, Option[RemoteConnection]) = {
    log.debug("createShellProcessHandler")
    val workingDirPath = getWorkingDirPath(project)
    val workingDir = new File(workingDirPath)

    val sbtSettings = getSbtSettings(workingDirPath)
    lazy val launcher = launcherJar(sbtSettings)

    // an id to identify this boot of sbt as being launched from idea, so that any plugins it injects are never ever loaded otherwise
    // use sbtStructureVersion as approximation of compatible versions of IDEA this is allowed to launch with.
    // this avoids failing reloads when multiple sbt instances are booted from IDEA (SCL-12009)
    val runid = BuildInfo.sbtStructureVersion

    val sdk = selectSdkOrWarn(sbtSettings)
    val javaParameters: JavaParameters = new JavaParameters
    javaParameters.setJdk(sdk)
    javaParameters.configureByProject(project, 1, sdk)
    javaParameters.setWorkingDirectory(workingDir)
    javaParameters.setJarPath(launcher.getCanonicalPath)

    val debugConnection = if (sbtSettings.shellDebugMode) Option(addDebugParameters(javaParameters)) else None

    invokeAndWait {
      inWriteAction {
        //By saving all documents ew ensure that edits in `project/build.properties` are saved to disk
        //otherwise user might change `sbt.version`, reload the project and there will be a warning in sbt shell
        //"[warn] sbt version mismatch, using: 1.9.1, in build.properties: "1.9.2", use 'reboot' to use the new value."
        //This is because `saveAllDocuments` will be called anyway after sbt process is started, but before it does the check which produces the warning
        FileDocumentManager.getInstance().saveAllDocuments()
      }
    }

    val projectSbtVersion = Version(detectSbtVersion(workingDir, launcher))

    val autoPluginsSupported = projectSbtVersion >= SbtProjectResolver.sinceSbtVersionShell
    val addPluginSupported = addPluginCommandSupported(projectSbtVersion)

    val vmParams = javaParameters.getVMParametersList
    vmParams.add("-server")

    val sbtOpts = SbtUtil.collectAllOptionsFromSbt(sbtSettings.sbtOptions, workingDir, sbtSettings.passParentEnvironment, sbtSettings.userSetEnvironment)
    val sbtOptsValues = sbtOpts.collect { case a: JvmOption => a.value }
    val allOpts = buildVMParameters(sbtSettings, workingDir, sbtOptsValues)
    vmParams.addAll(allOpts.asJava)

    // don't add runid when using addPluginSbtFile command
    if (! addPluginSupported)
      vmParams.add(s"-Didea.runid=$runid")

    // For details see: https://youtrack.jetbrains.com/issue/SCL-13293#focus=streamItem-27-3323121.0-0
    if(SystemInfo.isWindows)
      vmParams.add("-Dsbt.log.noformat=true")

    val commandLine: GeneralCommandLine = javaParameters.toCommandLine
    getCustomVMExecutableOrWarn(sbtSettings).foreach(exe => commandLine.setExePath(exe.getAbsolutePath))

    if (autoPluginsSupported) {
      val sbtMajorVersion = binaryVersion(projectSbtVersion)

      val globalPluginsDir = globalPluginsDirectory(sbtMajorVersion, commandLine.getParametersList)
      // workaround: --addPluginSbtFile fails if global plugins dir does not exist. https://youtrack.jetbrains.com/issue/SCL-14415
      globalPluginsDir.mkdirs()

      val globalSettingsFile = new File(globalPluginsDir, "idea.sbt")

      val settingsFile =
        if (addPluginSupported) FileUtil.createTempFile("idea", Sbt.Extension, true)
        else globalSettingsFile

      // caution! writes injected plugin settings to user's global sbt config if addPlugin command is not supported
      val plugins = injectedPlugins(sbtMajorVersion.presentation)
      injectSettings(runid, ! addPluginSupported, settingsFile, pluginResolverSetting +: plugins)

      if (addPluginSupported) {
        val settingsPath = settingsFile.getAbsolutePath
        commandLine.addParameter("early(addPluginSbtFile=\"\"\"" + settingsPath + "\"\"\")")
      }

      val compilerIndicesPluginLoaded = plugins.exists(_.contains("sbt-idea-compiler-indices"))
      val ideaPort = SbtCompilationSupervisorPort.port
      val ideaPortSetting = if (ideaPort == -1) "" else s"; set ideaPort in Global := $ideaPort ;"

      // we have our plugins in there, load custom shell
      val commands =
        if (compilerIndicesPluginLoaded) s"$ideaPortSetting idea-shell"
        else                             "idea-shell"

      commandLine.addParameter(commands)
      val sbtLauncherArgs = sbtOpts.collect { case a: SbtLauncherOption => a.value }
      commandLine.addParameters(sbtLauncherArgs.asJava)
    }

    val pty = createPtyCommandLine(commandLine, sbtSettings.passParentEnvironment, sbtSettings.userSetEnvironment)
    val cpty = new ColoredProcessHandler(pty)
    cpty.setShouldKillProcessSoftly(true)
    patchWindowSize(cpty.getProcess)

    (cpty, debugConnection)
  }

  private def getWorkingDirPath(project: Project): String = {
    //Fist try to calculate root path based on `getExternalRootProjectPath`
    //When sbt project reference another sbt project via `RootProject` this will correctly find the root project path (see SCL-21143)
    //However, if user manually linked multiple SBT projects via external system tool window (sbt tool window)
    //using "Link sbt Project" button (the one with "plus" icon), it  will randomly choose one of the projects
    val externalRootProjectPath: Option[String] = {
      val modules = ModuleManager.getInstance(project).getModules.toSeq
      modules.iterator.map(ExternalSystemApiUtil.getExternalRootProjectPath).find(_ != null)
    }
    externalRootProjectPath
      .orElse {
        //Not sure when externalRootProjectPath can be empty in SBT projects
        //But just in case fallback to ProjectUtil.guessProjectDir, but notice that it's not reliable in some cases (see SCL-21143)
        val message = s"Can't calculate external root project path for project `${project.getName}`, fallback to `ProjectUtil.guessProjectDir`"
        if (ApplicationManager.getApplication.isInternal)
          log.error(message)
        else
          log.warn(message)
        Option(ProjectUtil.guessProjectDir(project)).map(_.getCanonicalPath)
      }
      .getOrElse(throw new IllegalStateException(s"no project directory found for project ${project.getName}"))
  }

  // on Windows the terminal defaults to 80 columns which wraps and breaks highlighting.
  // Use a wider value that should be reasonable in most cases. Has no effect on Unix.
  // TODO perhaps determine actual width of window and adapt accordingly
  private def patchWindowSize(process: Process): Unit = if (!ApplicationManager.getApplication.isUnitTestMode) {
    process match {
      case _: UnixPtyProcess => // don't need to do stuff
      case proc: PtyProcess  => proc.setWinSize(new WinSize(2000, 100))
      case _                 =>
    }
  }

  /** If a custom VM executable is configured, return it. If it's not a valid path, warn user. */
  private def getCustomVMExecutableOrWarn(sbtSettings: SbtExecutionSettings): Option[File] =
    Option(sbtSettings.vmExecutable).filter { path =>
      if (path.isFile) true
      else {
        val badCustomVMNotification =
          ScalaNotificationGroups.sbtShell
            .createNotification(SbtBundle.message("sbt.shell.no.jre.found.at.path", sbtSettings.vmExecutable), NotificationType.WARNING)
        badCustomVMNotification.addAction(ConfigureSbtAction)
        badCustomVMNotification.notify(project)
        false
      }
    }

  private object ConfigureSbtAction extends NotificationAction(SbtBundle.message("sbt.shell.configure.sbt.jvm")) {

    override def actionPerformed(e: AnActionEvent, notification: Notification): Unit = {
      // External system handles the Configurable name for sbt settings
      ShowSettingsUtil.getInstance().showSettingsDialog(project, SbtProjectSystem.Id.getReadableName)
      notification.expire()
    }
  }

  private def selectSdkOrWarn(sbtSettings: SbtExecutionSettings): Sdk = {

    val configuredSdk = sbtSettings.jdk.map(JdkByName).flatMap(SdkUtils.findProjectSdk)
    val projectSdk = ProjectRootManager.getInstance(project).getProjectSdk

    configuredSdk.getOrElse {
      if (projectSdk != null) projectSdk
      else {
        val message = SbtBundle.message("sbt.shell.no.project.jdk.configured")
        val noProjectSdkNotification =
          ScalaNotificationGroups.sbtShell.createNotification(message, NotificationType.ERROR)
        noProjectSdkNotification.addAction(ConfigureProjectJdkAction)
        noProjectSdkNotification.notify(project)
        throw new RuntimeException(message)
      }
    }
  }

  private object ConfigureProjectJdkAction extends NotificationAction(SbtBundle.message("sbt.shell.configure.project.jdk")) {

    // copied from ShowStructureSettingsAction
    override def actionPerformed(e: AnActionEvent, notification: Notification): Unit = {
      new SingleConfigurableEditor(project, ProjectStructureConfigurable.getInstance(project), SettingsDialog.DIMENSION_KEY) {
        override protected def getStyle = DialogStyle.COMPACT
      }.show()
      notification.expire()
    }
  }

  /**
   * add debug parameters to java parameters and create remote connection
   * @return
   */
  private def addDebugParameters(javaParameters: JavaParameters): RemoteConnection = {

    val host = "localhost"
    val port = DebuggerUtils.getInstance.findAvailableDebugAddress(true)
    val remoteConnection = new RemoteConnection(true, host, port, false)

    val shellDebugProperties = s"-agentlib:jdwp=transport=dt_socket,address=$host:$port,suspend=n,server=y"
    val vmParams = javaParameters.getVMParametersList
    vmParams.prepend("-Xdebug")
    vmParams.replaceOrPrepend("-agentlib:jdwp=", shellDebugProperties)

    remoteConnection
  }

  private def getSbtSettings(dir: String) = SbtExternalSystemManager.executionSettingsFor(project, dir)

  private def launcherJar(sbtSettings: SbtExecutionSettings): File =
    sbtSettings.customLauncher.getOrElse(getDefaultLauncher)

  /**
   * Because the regular GeneralCommandLine process doesn't mesh well with JLine on Windows, use a
   * Pseudo-Terminal based command line
   * @param commandLine commandLine to copy from
   * @return
   */
  private def createPtyCommandLine(commandLine: GeneralCommandLine, passParentEnvironment: Boolean, environment: Map[String, String]) = {
    val pty = new PtyCommandLine()
    pty.withExePath(commandLine.getExePath)
    pty.withWorkDirectory(commandLine.getWorkDirectory)
    pty.withEnvironment(commandLine.getEnvironment)
    pty.withEnvironment(environment.asJava)
    pty.withParameters(commandLine.getParametersList.getList)
    val parentEnvironmentType = if (passParentEnvironment) commandLine.getParentEnvironmentType else ParentEnvironmentType.NONE
    pty.withParentEnvironmentType(parentEnvironmentType)

    pty
  }

  /**
   * Inject custom settings or plugins into an sbt directory.
   * This seems to be the most straightforward way to add our own sbt settings
   */
  private def injectSettings(runid: String, guardSettings: Boolean, settingsFile: File, settings: Seq[String]): Unit = {
    @NonNls
    val header =
      """// Generated by IntelliJ-IDEA Scala plugin.
        |// Adds settings when starting sbt from IDEA.
        |// Manual changes to this file will be lost.
        |""".stripMargin
    val settingsString = settings.mkString("scala.collection.Seq(\n",",\n","\n)")

    // any idea-specific settings should be added conditional on sbt being started from idea
    val guardedSettings =
      if (guardSettings)
        s"""if (java.lang.System.getProperty("idea.runid", "false") == "$runid") $settingsString else scala.collection.Seq.empty"""
      else settingsString

    val content = header + "\n" + guardedSettings

    try {
      FileUtil.writeToFile(settingsFile, content)
    } catch {
      case x : IOException =>
        log.error(s"unable to write ${settingsFile.getPath} which is required for sbt shell support", x)
        throw x
    }
  }

  /** asynchronously initializes SbtShellRunner with sbt process, console ui and opens sbt shell window */
  def initAndRunAsync(): Unit = {
    log.debug("initAndRunAsync")
    executeOnPooledThread {
      val runner = acquireShellRunner()
      runner.openShell(true)
    }
  }

  private def updateProcessData(): ProcessData = {
    log.trace("updateProcessData")
    val pd = createProcessData()
    processData.synchronized {
      processData = Some(pd)
      pd.runner.initAndRun()
    }
    pd
  }

  private def createProcessData(): ProcessData = {
    val (handler, debugConnection) = createShellProcessHandler()
    val title = project.getName
    val runner = new SbtShellRunner(project, title, debugConnection)
    ProcessData(handler, runner)
  }

  /** Supply a PrintWriter that writes to the current process. */
  def usingWriter[T](f: PrintWriter => T): T = {
    val processInput  = acquireShellProcessHandler().getProcessInput
    val writer = new PrintWriter(new OutputStreamWriter(processInput))
    f(writer)
  }

  /** Request an sbt shell process instance. It will be started if necessary.
   * The process handler should only be used to access the running process!
   * SbtProcessManager is solely responsible for handling the running state.
   */
  private[shell] def acquireShellProcessHandler(): ColoredProcessHandler = processData.synchronized {
    log.trace("acquireShellProcessHandler")
    processData match {
      case Some(data@ProcessData(handler, _)) if isAlive(data) =>
        handler
      case _ =>
        updateProcessData().processHandler
    }
  }

  /** Creates the SbtShellRunner view if necessary. */
  def acquireShellRunner(): SbtShellRunner = processData.synchronized {
    log.trace("processData")
    processData match {
      case Some(data@ProcessData(_, runner)) if isAlive(data) =>
        runner
      case _ =>
        updateProcessData().runner
    }
  }

  def shellRunner: Option[SbtShellRunner] = processData.map(_.runner)

  def restartProcess(): Unit = processData.synchronized {
    log.debug("restartProcess")
    destroyProcess()
    updateProcessData()
  }

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

  def destroyProcess(): Unit = processData.synchronized {
    log.debug("destroyProcess")
    processData match {
      case Some(ProcessData(handler, _)) =>
        val runnable: Runnable = () => terminateProcessGracefully(handler.getProcess)
        ProgressManager.getInstance().runProcessWithProgressSynchronously(runnable, SbtBundle.message("sbt.shell.stopping.process"), false, project)
        processData = None
      case None => // nothing to do
    }
  }

  def sendSigInt(): Unit = processData.foreach(_.processHandler.destroyProcess())

  override def dispose(): Unit = {
    destroyProcess()
    SbtShellConsoleView.disposeLastConsoleView(project)
  }

  /** Report if shell process is alive. Should only be used for UI/informational purposes. */
  private[shell] def isAlive: Boolean =
    processData.exists(isAlive)

  private def isAlive(processData: ProcessData): Boolean = {
    // processData.processHandler.getProcess.isAlive // TODO: I am not sure which is the best
    !processData.processHandler.isProcessTerminated
  }
}

object SbtProcessManager {

  def forProject(project: Project): SbtProcessManager = {
    val pm = project.getService(classOf[SbtProcessManager])
    if (pm == null) throw new IllegalStateException(s"unable to get component SbtProcessManager for project $project")
    else pm
  }

  private[sbt] def instanceIfCreated(project: Project): Option[SbtProcessManager] = {
    Option(project.getServiceIfCreated(classOf[SbtProcessManager]))
  }

  private case class ProcessData(processHandler: ColoredProcessHandler,
                                 runner: SbtShellRunner)

  private[shell]
  def buildVMParameters(sbtSettings: SbtExecutionSettings, workingDir: File, sbtOpts: Seq[String]): Seq[String] = {
    val hardcoded = List("-Dsbt.supershell=false")
    val jvmOpts = hardcoded ++
      SbtUtil.collectAllOptionsFromJava(workingDir, sbtSettings.vmOptions, sbtSettings.passParentEnvironment, sbtSettings.userSetEnvironment) ++
      sbtOpts

    val hasXmx = jvmOpts.exists(_.startsWith("-Xmx"))
    val xmsPrefix = "-Xms"
    def minMaxHeapSize = jvmOpts.reverseIterator
      .find(_.startsWith(xmsPrefix))
      .map(_.drop(xmsPrefix.length))
      .flatMap(JvmMemorySize.parse)
    def xmxNotNeeded = minMaxHeapSize.exists(_ >= sbtSettings.hiddenDefaultMaxHeapSize)

    if (hasXmx || xmxNotNeeded) jvmOpts
    else ("-Xmx" + sbtSettings.hiddenDefaultMaxHeapSize) +: jvmOpts
  }
}
