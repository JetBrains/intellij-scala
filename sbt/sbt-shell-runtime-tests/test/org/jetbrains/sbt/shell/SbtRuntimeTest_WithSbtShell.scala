package org.jetbrains.sbt.shell

import com.intellij.execution.process.OSProcessHandler
import com.intellij.openapi.util.Disposer
import org.jetbrains.plugins.scala.SlowTests2
import org.jetbrains.sbt.SbtRuntimeTestBase
import org.junit.experimental.categories.Category

import scala.compiletime.uninitialized
import scala.concurrent.duration.FiniteDuration

@Category(Array(classOf[SlowTests2]))
abstract class SbtRuntimeTest_WithSbtShell extends SbtRuntimeTestBase {

  protected def useNewShell: Boolean = false

  override protected def getTestSbtProjectSettings =
    super.getTestSbtProjectSettings.copy(useSbtShellForImport = true)

  //TODO: leave just one method
  protected def comm: SbtShellCommunication = sbtShellFixture.getSbtShellCommunication
  protected def shellCommunication: SbtShellCommunication = comm

  protected def shellProcessHandler: OSProcessHandler = sbtShellFixture.getSbtShellProcessHandler

  protected def processListener: SbtShellTestUtil.TestSbtShellProcessListener = sbtShellFixture.getTestSbtShellProcessListener

  protected var sbtShellFixture: SbtShellTestFixture = uninitialized

  // SbtShellTestFixture setUp requires BGT
  override def runInDispatchThread(): Boolean = false

  protected val DefaultCommandWaitTimeout: FiniteDuration = SbtShellTestFixture.DefaultCommandWaitTimeout

  override protected def setupBeforeProjectImport(): Unit = {
    super.setupBeforeProjectImport()
    SbtShellTestUtil.setNewSbtShellEnabled(useNewShell, getTestRootDisposable)
  }

  override def setUp(): Unit = {
    super.setUp()

    val project = getMyProject

    testProjectJdk.setUp()
    testProjectJdk.setAsProjectJdk(project)

    sbtShellFixture = new SbtShellTestFixture(project)
    Disposer.register(getTestRootDisposable, sbtShellFixture)
    sbtShellFixture.setUp()
  }
}
