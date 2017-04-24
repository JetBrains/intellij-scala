package org.jetbrains.sbt.shell

import java.io.File

import com.intellij.execution.process.{ProcessEvent, ProcessListener}
import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.{Disposer, Key}
import com.intellij.testFramework.{PlatformTestCase, ThreadTracker}
import com.intellij.util.ui.UIUtil
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.plugins.scala.util.TestUtils
import org.jetbrains.sbt.project.SbtProjectSystem

/**
  * Created by Roman.Shein on 27.03.2017.
  */
abstract class SbtProjectPlatformTestCase extends PlatformTestCase {
  override def setUpProject(): Unit = {
    //projectFile is the sbt file for the root project
    val project = ProjectUtil.openOrImport(getSbtRootFile.getAbsolutePath, null, false)
    import org.jetbrains.plugins.scala.extensions._
    val sdk = TestUtils.createJdk()
    inWriteAction{
      ProjectJdkTable.getInstance.addJdk(sdk)
      ProjectRootManager.getInstance(project).setProjectSdk(sdk)
    }
    ExternalSystemUtil.refreshProjects(new ImportSpecBuilder(project, SbtProjectSystem.Id))
    myProject = project
  }

  def getBasePath: String = TestUtils.getTestDataPath

  def getPath: String

  def getBuildFileName: String = "build.sbt"

  def getSbtRootFile: File = new File(getBasePath + "/" + getPath + "/" + getBuildFileName)

  override protected def setUpModule() = {}

  override protected def setUpJdk() = {}

  override def setUp(): Unit = {
    super.setUp()
    //TODO this is hacky, but sometimes 'main' gets leaked
    ThreadTracker.longRunningThreadCreated(ApplicationManager.getApplication, "ForkJoinPool")
    myComm = SbtShellCommunication.forProject(getProject)
    myRunner = SbtProcessManager.forProject(getProject).openShellRunner()
    myRunner.getProcessHandler.addProcessListener(logger)
  }

  override def tearDown(): Unit = {
    myRunner.getConsoleView.dispose()
    Disposer.dispose(myRunner.getConsoleView)
    UIUtil.dispatchAllInvocationEvents()
    val handler = myRunner.getProcessHandler
    handler.destroyProcess()
    //give the handler some time to terminate the process
    while (!handler.isProcessTerminated || handler.isProcessTerminating) {
      Thread.sleep(500)
    }
    ProjectManager.getInstance().closeProject(myProject)
    MavenProjectsManager.getInstance(myProject).disposeComponent()
    super.tearDown()
    //remove links so that we don't leak the project
    myProject = null
    myComm = null
    myRunner = null
  }


  protected def comm = myComm
  protected def runner = myRunner
  protected var myComm: SbtShellCommunication = _
  protected var myRunner: SbtShellRunner = _
  protected val logger: ProcessLogger = new ProcessLogger
}

object SbtProjectPlatformTestCase {
  val errorPrefix = "[error]"
}

class ProcessLogger extends ProcessListener {
  private val logBuilder: StringBuilder = new StringBuilder()

  def getLog: String = logBuilder.mkString

  override def processWillTerminate(event: ProcessEvent, willBeDestroyed: Boolean): Unit = {}

  override def startNotified(event: ProcessEvent): Unit = {}

  override def processTerminated(event: ProcessEvent): Unit = {}

  override def onTextAvailable(event: ProcessEvent, outputType: Key[_]): Unit = {
    synchronized { logBuilder.append(event.getText) }
    print(event.getText)
  }
}