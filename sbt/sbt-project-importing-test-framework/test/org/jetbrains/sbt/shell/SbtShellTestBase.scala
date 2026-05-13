package org.jetbrains.sbt.shell

import com.intellij.execution.process.OSProcessHandler
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import org.jetbrains.plugins.scala.SlowTests2
import org.jetbrains.sbt.SbtTestDataUtils
import org.jetbrains.sbt.project.SbtProjectStructureImportingLike
import org.junit.Assert.assertNotNull
import org.junit.experimental.categories.Category

import scala.concurrent.duration.{DurationInt, FiniteDuration}

@Category(Array(classOf[SlowTests2]))
abstract class SbtShellTestBase extends SbtProjectStructureImportingLike {

  protected def getRelativeTestProjectPath: String

  override protected def copyTestProjectToTemporaryDir: Boolean = true

  protected def useNewShell: Boolean = false

  override protected def getTestDataProjectPath: String =
    SbtTestDataUtils.resolveRelativePath(getRelativeTestProjectPath)

  protected def comm: SbtShellCommunication = myComm
  protected def shellProcessHandler: OSProcessHandler = myShellProcessHandler

  protected var myComm: SbtShellCommunication = _
  protected var myShellProcessHandler: OSProcessHandler = _
  protected var processListener: SbtShellTestUtil.TestSbtShellProcessListener = _

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

    SbtShellTestUtil.setNewSbtShellEnabled(useNewShell, getTestRootDisposable)

    importProject()

    val project = getMyProject

    myComm = SbtShellCommunication.forProject(project)
    assertNotNull(myComm)

    myShellProcessHandler = SbtShellTestUtil.acquireShellProcessHandler(project)

    processListener = new SbtShellTestUtil.TestSbtShellProcessListener
    myShellProcessHandler.addProcessListener(processListener)
  }

  override protected def importProject(): Unit = MyProxy.importProject(
    getMyProject,
    getExternalSystemId,
    getCurrentExternalProjectSettings,
    getProjectPath,
    createImportSpec(),
    handleImportFailure(_, _)
  )
}
