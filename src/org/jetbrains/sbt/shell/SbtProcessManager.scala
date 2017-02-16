package org.jetbrains.sbt.shell

import java.io.File

import com.intellij.execution.configurations.{GeneralCommandLine, JavaParameters, PtyCommandLine}
import com.intellij.execution.process.ColoredProcessHandler
import com.intellij.openapi.components.AbstractProjectComponent
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import org.jetbrains.sbt.project.SbtExternalSystemManager
import org.jetbrains.sbt.project.data.{JdkByName, SdkUtils}
import org.jetbrains.sbt.project.structure.SbtRunner

import scala.collection.JavaConverters._
/**
  * Manages the sbt shell process instance for the project.
  * Instantiates an sbt instance when initially requested.
  *
  * Created by jast on 2016-11-27.
  */
// TODO transparently support shell or server process
class SbtProcessManager(project: Project) extends AbstractProjectComponent(project) {

  @volatile private var myProcessHandler: Option[ColoredProcessHandler] = None
  @volatile private var myShellRunner: Option[SbtShellRunner] = None

  private def createShellProcessHandler(): ColoredProcessHandler = {
    val workingDir = project.getBaseDir.getCanonicalPath

    val sbtSettings = SbtExternalSystemManager.executionSettingsFor(project, workingDir)

    val projectSdk = ProjectRootManager.getInstance(project).getProjectSdk
    val configuredSdk = sbtSettings.jdk.map(JdkByName).flatMap(SdkUtils.findProjectSdk)
    val sdk = configuredSdk.getOrElse(projectSdk)
    assert(sdk != null, "Setup a project JDK to run the SBT shell with")
    val launcherJar: File = sbtSettings.customLauncher.getOrElse(SbtRunner.getDefaultLauncher)

    val javaParameters: JavaParameters = new JavaParameters
    javaParameters.setJdk(sdk)
    javaParameters.configureByProject(project, 1, sdk)
    javaParameters.setWorkingDirectory(workingDir)
    javaParameters.setJarPath(launcherJar.getCanonicalPath)
    // TODO make sure jvm also gets proxy settings
    val vmParams = javaParameters.getVMParametersList
    vmParams.addAll(sbtSettings.vmOptions.asJava)

    val commandLine: GeneralCommandLine = javaParameters.toCommandLine
    val pty = createPtyCommandLine(commandLine)
    new ColoredProcessHandler(pty)
  }

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
  def acquireShellProcessHandler: ColoredProcessHandler = myProcessHandler.synchronized {
    myProcessHandler match {
      case Some(handler) if handler.getProcess.isAlive => handler
      case _ =>
        val handler = createShellProcessHandler()
        myProcessHandler = Option(handler)
        handler
    }
  }

  /** Creates the SbtShellRunner view, or focuses it if it already exists. */
  def openShellRunner(focus: Boolean = false): SbtShellRunner = myProcessHandler.synchronized {

    myShellRunner match {
      case Some(runner) if runner.getConsoleView.isRunning =>
        ShellUIUtil.inUIsync(runner.openShell(focus))
        runner
      case _ =>
        val title = "SBT Shell"
        val runner = new SbtShellRunner(project, title)
        myShellRunner = Option(runner)
        runner.initAndRun()
        runner
    }
  }

  def restartProcess(): Unit = myProcessHandler.synchronized {
    destroyProcess()
    myProcessHandler = Option(createShellProcessHandler())
  }

  def destroyProcess(): Unit = {
    myProcessHandler.synchronized {
      myProcessHandler.foreach(_.destroyProcess())
      myProcessHandler = None
      myShellRunner = None
    }
  }

  override def projectClosed(): Unit = {
    disposeComponent()
  }

  override def disposeComponent(): Unit = {
    destroyProcess()
  }

}

object SbtProcessManager {
  def forProject(project: Project): SbtProcessManager = project.getComponent(classOf[SbtProcessManager])
}
