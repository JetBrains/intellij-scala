package org.jetbrains.sbt.shell

import com.intellij.execution.process.OSProcessHandler
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import org.jetbrains.sbt.shell.SbtShellTestFixture.SbtShellInitialisationTimeout
import org.junit.Assert.assertNotNull

import scala.compiletime.uninitialized
import scala.concurrent.duration.{DurationInt, FiniteDuration}

/**
 * ATTENTION: users of this class have to do:
 * ```scala
 * override def runInDispatchThread(): Boolean = false
 * ```
 */
final class SbtShellTestFixture(project: Project) extends Disposable {

  private var myCommunication: SbtShellCommunication = uninitialized
  private var myProcessHandler: OSProcessHandler = uninitialized
  private var myProcessListener: SbtShellTestUtil.TestSbtShellProcessListener = uninitialized

  def getSbtShellCommunication: SbtShellCommunication = myCommunication

  def getSbtShellProcessHandler: OSProcessHandler = myProcessHandler

  // Q: is it indeed needed by default in all tests or is it something optional?
  def getTestSbtShellProcessListener: SbtShellTestUtil.TestSbtShellProcessListener = myProcessListener

  @RequiresBackgroundThread
  def setUp(): Unit = {
    myCommunication = SbtShellCommunication.forProject(project)
    assertNotNull(myCommunication)

    myProcessHandler = SbtShellTestUtil.acquireShellProcessHandler(project)

    myProcessListener = new SbtShellTestUtil.TestSbtShellProcessListener
    myProcessHandler.addProcessListener(myProcessListener)
  }

  def waitForShellReady(project: Project): Unit =
    SbtShellTestUtil.waitUntilSbtShellIsReady(
      project,
      SbtShellInitialisationTimeout,
      "Timed out waiting for sbt shell to initialize before reading quoted path options"
    )

  override def dispose(): Unit = {
    if (myCommunication != null) {
      myCommunication.clearTestStateListeners()
    }
    if (myProcessHandler != null && myProcessListener != null) {
      myProcessHandler.removeProcessListener(myProcessListener)
    }
  }
}

object SbtShellTestFixture {
  val DefaultCommandWaitTimeout: FiniteDuration = 60.seconds
  val SbtShellInitialisationTimeout: FiniteDuration = 60.seconds
}
