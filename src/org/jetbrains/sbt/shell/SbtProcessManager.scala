package org.jetbrains.sbt.shell

import java.io.{File, IOException, OutputStreamWriter, PrintWriter}

import com.intellij.debugger.impl.{DebuggerManagerImpl, GenericDebuggerRunnerSettings}
import com.intellij.execution.configurations._
import com.intellij.execution.process.{ColoredProcessHandler, ProcessAdapter}
import com.intellij.openapi.components.AbstractProjectComponent
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.plugins.scala.buildinfo.BuildInfo
import org.jetbrains.plugins.scala.project.Version
import org.jetbrains.sbt.SbtUtil
import org.jetbrains.sbt.project.SbtExternalSystemManager
import org.jetbrains.sbt.project.data.{JdkByName, SdkUtils}
import org.jetbrains.sbt.project.settings.SbtExecutionSettings
import org.jetbrains.sbt.project.structure.{SbtOpts, SbtRunner}

import scala.collection.JavaConverters._
/**
  * Manages the sbt shell process instance for the project.
  * Instantiates an sbt instance when initially requested.
  *
  * Created by jast on 2016-11-27.
  */
class SbtProcessManager(project: Project) extends AbstractProjectComponent(project) {

  import SbtProcessManager.ProcessData

  @volatile private var processData: Option[ProcessData] = None

  /** Plugins injected into user's global sbt build. */
  // TODO add configurable plugins somewhere for users and via API; factor this stuff out
  private def injectedPlugins(sbtMajorVersion: Version): Seq[String] =
    sbtStructurePlugin(sbtMajorVersion)

  // this *might* get messy if multiple IDEA projects start messing with the global settings.
  // but we should be fine since it is written before every sbt boot
  private def sbtStructurePlugin(sbtMajorVersion: Version): Seq[String] = {
    // IDEA won't import the shared source dir between build definition and build, so this red
    val sbtStructureVersion = BuildInfo.sbtStructureVersion
    val sbtIdeaShellVersion = BuildInfo.sbtIdeaShellVersion
    sbtMajorVersion.presentation match {
      case "0.12" => Seq.empty // 0.12 doesn't support AutoPlugins
      case _ => Seq(
          s"""addSbtPlugin("org.jetbrains" % "sbt-structure-extractor" % "$sbtStructureVersion")""",
          s"""addSbtPlugin("org.jetbrains" % "sbt-idea-shell" % "$sbtIdeaShellVersion")"""
        ) // works for 0.13.5+, for older versions it will be ignored
    }
  }


  private def createShellProcessHandler(): (ColoredProcessHandler, Option[RemoteConnection]) = {
    val workingDirPath = project.getBaseDir.getCanonicalPath
    val workingDir = new File(workingDirPath)

    val sbtSettings = getSbtSettings(workingDirPath)
    lazy val launcher = launcherJar(sbtSettings)

    val projectSbtVersion = Version(SbtUtil.detectSbtVersion(workingDir, launcher))
    val autoPluginsSupported = projectSbtVersion >= SbtRunner.sinceSbtVersionShell

    // an id to identify this boot of sbt as being launched from idea, so that any plugins it injects are never ever loaded otherwise
    // use sbtStructureVersion as approximation of compatible versions of IDEA this is allowed to launch with.
    // this avoids failing reloads when multiple sbt instances are booted from IDEA (SCL-12009)
    val runid = BuildInfo.sbtStructureVersion

    val projectSdk = ProjectRootManager.getInstance(project).getProjectSdk
    val configuredSdk = sbtSettings.jdk.map(JdkByName).flatMap(SdkUtils.findProjectSdk)
    val sdk = configuredSdk.getOrElse(projectSdk)
    // TODO prompt user to setup a JDK
    assert(sdk != null, "Setup a project JDK to run the SBT shell")

    val javaParameters: JavaParameters = new JavaParameters
    javaParameters.setJdk(sdk)
    javaParameters.configureByProject(project, 1, sdk)
    javaParameters.setWorkingDirectory(workingDir)
    javaParameters.setJarPath(launcher.getCanonicalPath)
    val debugConnection: Option[RemoteConnection] = if (sbtSettings.shellDebugMode) {
      val debuggerSettings = new GenericDebuggerRunnerSettings()
      debuggerSettings.setLocal(false) // I guess this means the thing being debugged is???
      // this will actually patch the javaParameters as a side effect
      Option(DebuggerManagerImpl.createDebugParameters(javaParameters, debuggerSettings, true))
    } else None

    val vmParams = javaParameters.getVMParametersList
    vmParams.add("-server")
    vmParams.addAll(SbtOpts.loadFrom(workingDir).asJava)
    vmParams.addAll(sbtSettings.vmOptions.asJava)
    vmParams.add(s"-Didea.runid=$runid")

    val commandLine: GeneralCommandLine = javaParameters.toCommandLine

    if (autoPluginsSupported) {
      val sbtMajorVersion = SbtUtil.binaryVersion(projectSbtVersion)
      val globalPluginsDir = SbtUtil.globalPluginsDirectory(sbtMajorVersion)

      // evil side effect! writes injected plugin settings to user's global sbt config
      injectSettings(runid, injectedPlugins(sbtMajorVersion), globalPluginsDir)
      // we have our plugins in there, load custom shell
      commandLine.addParameter("idea-shell")
    }

    val pty = createPtyCommandLine(commandLine)
    val cpty = new ColoredProcessHandler(pty)

    (cpty, debugConnection)
  }

  private def getSbtSettings(dir: String) = SbtExternalSystemManager.executionSettingsFor(project, dir)

  private def launcherJar(sbtSettings: SbtExecutionSettings): File =
    sbtSettings.customLauncher.getOrElse(SbtRunner.getDefaultLauncher)

  /**
    * Because the regular GeneralCommandLine process doesn't mesh well with JLine on Windows, use a
    * Pseudo-Terminal based command line
    * @param commandLine commandLine to copy from
    * @return
    */
  private def createPtyCommandLine(commandLine: GeneralCommandLine) = {
    val pty = new PtyCommandLine()
    pty.withExePath(commandLine.getExePath)
    pty.withWorkDirectory(commandLine.getWorkDirectory)
    pty.withEnvironment(commandLine.getEnvironment)
    pty.withParameters(commandLine.getParametersList.getList)
    pty.withParentEnvironmentType(commandLine.getParentEnvironmentType)

    pty
  }

  /** Request an sbt shell process instance. It will be started if necessary.
    * The process handler should only be used to access the running process!
    * SbtProcessManager is solely responsible for handling the running state.
    */
  private[shell] def acquireShellProcessHandler: ColoredProcessHandler = processData.synchronized {
    processData match {
      case Some(ProcessData(handler, _)) if handler.getProcess.isAlive =>
        handler
      case _ =>
        updateProcessData().processHandler
    }
  }

  /**
    * Inject custom settings or plugins into an sbt directory.
    * This seems to be the most straightforward way to add our own sbt settings
    */
  private def injectSettings(runid: String, settings: Seq[String], dir: File) = {
    val header =
      """// Generated by IntelliJ-IDEA Scala plugin.
        |// Add settings when starting sbt from IDEA.
        |// Manual changes to this file will be lost.
      """.stripMargin.trim
    val settingsString = settings.mkString("scala.collection.Seq(\n",",\n","\n)")

    // any idea-specific settings should be added conditional on sbt being started from idea
    val conditionalSettings =
      s"""if (java.lang.System.getProperty("idea.runid", "false") == "$runid") $settingsString else scala.collection.Seq.empty"""

    val content = header + "\n" + conditionalSettings

    val file = new File(dir, "idea.sbt")

    try {
      FileUtil.writeToFile(file, content)
    } catch {
      case _ : IOException =>
        // TODO log this!
    }
  }

  private def updateProcessData(): ProcessData = {
    val (handler, debugConnection) = createShellProcessHandler()

    val title = project.getName
    val runner = new SbtShellRunner(project, title, debugConnection)

    val pd = ProcessData(handler, runner)

    processData = Option(pd)
    pd.runner.initAndRun()
    pd
  }

  def attachListener(listener: ProcessAdapter): Unit =
    acquireShellProcessHandler.addProcessListener(listener)

  def removeListener(listener: ProcessAdapter): Unit =
    acquireShellProcessHandler.removeProcessListener(listener)

  /** Supply a printwriter that writes to the current process. */
  def usingWriter[T](f: PrintWriter => T): T = {
    val writer = new PrintWriter(new OutputStreamWriter(acquireShellProcessHandler.getProcessInput))
    f(writer)
  }

  /** Creates the SbtShellRunner view, or focuses it if it already exists. */
  def openShellRunner(focus: Boolean = false): SbtShellRunner = {

    val theRunner = processData match {
      case Some(ProcessData(_, runner)) if runner.getConsoleView.isRunning =>
        runner
      case _ =>
        updateProcessData().runner
    }

    ShellUIUtil.inUIsync(theRunner.openShell(focus))

    theRunner
  }

  def restartProcess(): Unit = processData.synchronized {
    destroyProcess()
    updateProcessData()
  }

  def destroyProcess(): Unit = processData.synchronized {
    processData match {
      case Some(ProcessData(handler, _)) =>
        handler.destroyProcess()
        processData = None
      case None => // nothing to do
    }
  }

  override def projectClosed(): Unit = {
    destroyProcess()
  }
}

object SbtProcessManager {
  def forProject(project: Project): SbtProcessManager = project.getComponent(classOf[SbtProcessManager])

  private case class ProcessData(processHandler: ColoredProcessHandler,
                                 runner: SbtShellRunner)
}
