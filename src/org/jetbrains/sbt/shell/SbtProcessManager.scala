package org.jetbrains.sbt.shell

import java.io.File

import com.intellij.execution.configurations.{GeneralCommandLine, JavaParameters}
import com.intellij.execution.process.{ColoredProcessHandler, OSProcessHandler}
import com.intellij.openapi.components.AbstractProjectComponent
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.{JavaSdkType, JdkUtil, Sdk, SdkTypeId}
import com.intellij.openapi.roots.ProjectRootManager
import org.jetbrains.sbt.project.structure.SbtRunner

/**
  * Manages the sbt shell process instance for the project.
  * Instantiates an sbt instance when initially requested.
  *
  * Created by jast on 2016-11-27.
  */
// TODO try to make sure there is only one shell or server running. Connect to externally running shell if practical
// TODO transparently support shell or server process
class SbtProcessManager(project: Project) extends AbstractProjectComponent(project) {

  @volatile private var myProcessHandler: Option[ColoredProcessHandler] = None


  private def createShellProcessHandler(): ColoredProcessHandler = {
    val sdk: Sdk = ProjectRootManager.getInstance(project).getProjectSdk
    assert(sdk != null) // TODO user feedback, this gets reported only as assertion error
    val sdkType: SdkTypeId = sdk.getSdkType
    assert(sdkType.isInstanceOf[JavaSdkType])
    val exePath: String = sdkType.asInstanceOf[JavaSdkType].getVMExecutablePath(sdk)
    val workingDir = project.getBaseDir.getCanonicalPath

    // TODO get this from configuration
    val launcherJar: File = SbtRunner.getDefaultLauncher

    val javaParameters: JavaParameters = new JavaParameters
    javaParameters.setJdk(sdk)
    javaParameters.configureByProject(project, 1, sdk)
    javaParameters.setWorkingDirectory(workingDir)
    javaParameters.setJarPath(launcherJar.getCanonicalPath)
    // TODO get from configuration, correct java 6/7/8-appropriate args
    javaParameters.getVMParametersList.addAll("-XX:MaxPermSize=128M", "-Xmx2G", "-Dsbt.log.noformat=false")

    val commandLine: GeneralCommandLine = JdkUtil.setupJVMCommandLine(exePath, javaParameters, false)
    new ColoredProcessHandler(commandLine)
  }

  /** Request an sbt shell process instance. It will be started if necessary.
    * The process handler should only be used to access the running process!
    * SbtProcessManager is solely responsible for handling the running state.
    */
  def acquireShellProcessHandler: ColoredProcessHandler = myProcessHandler.synchronized {
    (for {
      handler <- myProcessHandler
      if handler.getProcess.isAlive
    } yield handler)
      .getOrElse {
        myProcessHandler = Option(createShellProcessHandler())
        myProcessHandler.get
      }
  }

  def restartProcess(): Unit = myProcessHandler.synchronized {
    destroyProcess()
    myProcessHandler = Option(createShellProcessHandler())
  }

  def destroyProcess(): Unit = myProcessHandler.synchronized {
    myProcessHandler.foreach(_.destroyProcess())
    myProcessHandler = None
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
