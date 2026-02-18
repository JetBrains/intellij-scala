package org.jetbrains.sbt.shell

import com.intellij.execution.process.OSProcessHandler
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import org.jetbrains.plugins.scala.SlowTests2
import org.jetbrains.plugins.scala.util.TestUtils
import org.jetbrains.sbt.project.SbtProjectStructureImportingLike
import org.jetbrains.sbt.shell.SbtShellTestBase.ProcessLogger
import com.intellij.execution.process.{ProcessEvent, ProcessListener}
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.registry.Registry
import org.junit.Assert.assertNotNull
import org.junit.experimental.categories.Category

import java.nio.file.Path
import scala.concurrent.{Future, Promise}
import scala.concurrent.duration.{DurationInt, FiniteDuration}

@Category(Array(classOf[SlowTests2]))
abstract class SbtShellTestBase extends SbtProjectStructureImportingLike {

  protected def getRelativeTestProjectPath: String

  override protected def copyTestProjectToTemporaryDir: Boolean = true

  protected def useNewShell: Boolean = false

  override protected def getTestDataProjectPath: String =
    Path.of(TestUtils.getTestDataPath, getRelativeTestProjectPath).toString

  protected def comm: SbtShellCommunication = myComm
  protected def shellProcessHandler: OSProcessHandler = myShellProcessHandler

  protected var myComm: SbtShellCommunication = _
  protected var myShellProcessHandler: OSProcessHandler = _
  protected var logger: ProcessLogger = _

  protected val DefaultCommandWaitTimeout: FiniteDuration = 60.seconds

  override protected def setUpFixtures(): Unit = {
    val myTestFixture = IdeaTestFixtureFactory.getFixtureFactory
      .createFixtureBuilder(getName, getTestProjectPath, useDirectoryBasedStorageFormat()).getFixture
    myTestFixture.setUp()
    setMyTestFixture(myTestFixture)
  }

  override def setUp(): Unit = {
    getCurrentExternalProjectSettings.useSbtShellForImport = true
    super.setUp()

    if (useNewShell) {
      val newShellRegistry = Registry.get("sbt.new.shell")
      newShellRegistry.setValue(true, getTestRootDisposable)
    }

    importProject()

    val project = getMyProject

    myComm = SbtShellCommunication.forProject(project)
    assertNotNull(myComm)

    myShellProcessHandler = SbtProcessManager.forProject(project).acquireShellProcessHandler()
    assertNotNull(myShellProcessHandler)

    logger = new ProcessLogger
    myShellProcessHandler.addProcessListener(logger)
  }
}

object SbtShellTestBase {
  val errorPrefix = "[error]"

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
}
