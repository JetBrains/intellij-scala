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
import com.intellij.openapi.util.Key
import com.intellij.testFramework.{PlatformTestCase, ThreadTracker}
import com.intellij.util.ui.UIUtil
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.util.TestUtils
import org.jetbrains.sbt.project.SbtProjectSystem

import scala.concurrent.{Future, Promise}

/**
  * Created by Roman.Shein on 27.03.2017.
  */
abstract class SbtProjectPlatformTestCase extends PlatformTestCase {

  override def setUpProject(): Unit = {
    //projectFile is the sbt file for the root project
    val sbtRootFile = getSbtRootFile
    assert(sbtRootFile.exists, "expected path does not exist: " + sbtRootFile.getAbsolutePath)
    val path = getSbtRootFile.getAbsolutePath
    val project = ProjectUtil.openOrImport(path, null, false)
    assert(project != null, s"project at path $path was null")
    val sdk = TestUtils.createJdk()
    inWriteAction {
      ProjectJdkTable.getInstance.addJdk(sdk)
      ProjectRootManager.getInstance(project).setProjectSdk(sdk)
    }
    // I would attach a callback here to debug errors, but that overrides the default callback which deos the project updating ...
    val importSpec = new ImportSpecBuilder(project, SbtProjectSystem.Id).build()
    ExternalSystemUtil.refreshProjects(importSpec)
    myProject = project
  }

  def getBasePath: String = TestUtils.getTestDataPath

  def getPath: String

  def getBuildFileName: String = "build.sbt"

  def getSbtRootFile: File = new File(getBasePath + "/" + getPath + "/" + getBuildFileName)

  override protected def setUpModule(): Unit = {}

  override protected def setUpJdk(): Unit = {}

  override def setUp(): Unit = {
    super.setUp()
    //TODO this is hacky, but sometimes 'main' gets leaked
    ThreadTracker.longRunningThreadCreated(ApplicationManager.getApplication, "ForkJoinPool")
    myComm = SbtShellCommunication.forProject(getProject)
    myRunner = SbtProcessManager.forProject(getProject).acquireShellRunner
    myRunner.getProcessHandler.addProcessListener(logger)
  }

  override def tearDown(): Unit = try {
    inWriteAction {
      val jdkTable = ProjectJdkTable.getInstance()
      jdkTable.getAllJdks.foreach(jdkTable.removeJdk)
    }

    SbtProcessManager.forProject(getProject).destroyProcess()

    UIUtil.dispatchAllInvocationEvents()
    val handler = myRunner.getProcessHandler
    //give the handler some time to terminate the process
    while (!handler.isProcessTerminated || handler.isProcessTerminating) {
      Thread.sleep(100)
    }
    ProjectManager.getInstance().closeProject(myProject)
  } finally {
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
  private val termination = Promise.apply[Int]()

  def getLog: String = logBuilder.mkString
  def terminated: Future[Int] = termination.future

  override def processWillTerminate(event: ProcessEvent, willBeDestroyed: Boolean): Unit = {}

  override def startNotified(event: ProcessEvent): Unit = {}

  override def processTerminated(event: ProcessEvent): Unit =
    termination.success(event.getExitCode)

  override def onTextAvailable(event: ProcessEvent, outputType: Key[_]): Unit = {
    synchronized { logBuilder.append(event.getText) }
    print(event.getText)
  }
}
