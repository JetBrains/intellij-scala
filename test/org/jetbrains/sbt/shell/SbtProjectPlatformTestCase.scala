package org.jetbrains.sbt.shell

import java.io.File

import com.intellij.execution.process.{ProcessEvent, ProcessListener}
import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.Key
import com.intellij.testFramework.{PlatformTestCase, ThreadTracker}
import com.intellij.util.ui.UIUtil
import org.jetbrains.plugins.scala.util.TestUtils

/**
  * Created by Roman.Shein on 27.03.2017.
  */
abstract class SbtProjectPlatformTestCase extends PlatformTestCase {
  override def doCreateProject(projectFile: File): Project = {
    //projectFile is the sbt file for the root project
    val project = ProjectUtil.openProject(projectFile.getAbsolutePath, null, false)
    import org.jetbrains.plugins.scala.extensions._
    val sdk = TestUtils.createJdk()
    inWriteAction{
      ProjectJdkTable.getInstance.addJdk(sdk)
      ProjectRootManager.getInstance(project).setProjectSdk(sdk)
    }
    project
  }

  def getBasePath: String = TestUtils.getTestDataPath

  def getPath: String

  def getBuildFileName: String = "build.sbt"

  override def getIprFile: File = new File(getBasePath + "/" + getPath + "/" + getBuildFileName)

  override def setUp(): Unit = {
    super.setUp()
    ThreadTracker.longRunningThreadCreated(ApplicationManager.getApplication, "")
    runner.initAndRun()
    runner.getProcessHandler.addProcessListener(logger)
  }

  override def tearDown(): Unit = {
    EditorFactory.getInstance().releaseEditor(runner.getConsoleView.getHistoryViewer)
    EditorFactory.getInstance().releaseEditor(runner.getConsoleView.getConsoleEditor)
    UIUtil.dispatchAllInvocationEvents()
    runner.getProcessHandler.destroyProcess()
    super.tearDown()
  }

  protected lazy val comm: SbtShellCommunication = SbtShellCommunication.forProject(getProject)
  protected lazy val runner: SbtShellRunner = new SbtShellRunner(getProject, "Test console")
  protected lazy val logger: ProcessLogger = new ProcessLogger
}

class ProcessLogger extends ProcessListener {
  private val logBuilder: StringBuilder = new StringBuilder()

  def getLog: String = logBuilder.mkString

  override def processWillTerminate(event: ProcessEvent, willBeDestroyed: Boolean): Unit = {}

  override def startNotified(event: ProcessEvent): Unit = {}

  override def processTerminated(event: ProcessEvent): Unit = {}

  override def onTextAvailable(event: ProcessEvent, outputType: Key[_]): Unit = logBuilder.append(event.getText)
}