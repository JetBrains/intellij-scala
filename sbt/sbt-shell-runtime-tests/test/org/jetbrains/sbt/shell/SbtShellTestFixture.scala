package org.jetbrains.sbt.shell

import com.intellij.execution.process.OSProcessHandler
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import org.junit.Assert.assertNotNull

import scala.compiletime.uninitialized
import scala.concurrent.duration.{DurationInt, FiniteDuration}

final class SbtShellTestFixture(project: Project) extends Disposable {

  private var myCommunication: SbtShellCommunication = uninitialized
  private var myProcessHandler: OSProcessHandler = uninitialized
  private var myProcessListener: SbtShellTestUtil.TestSbtShellProcessListener = uninitialized

  def getSbtShellCommunication: SbtShellCommunication = myCommunication

  def getSbtShellProcessHandler: OSProcessHandler = myProcessHandler

  // Q: is it indeed needed by default in all tests or is it something optional?
  def getTestSbtShellProcessListener: SbtShellTestUtil.TestSbtShellProcessListener = myProcessListener

  def setUp(): Unit = {
    myCommunication = SbtShellCommunication.forProject(project)
    assertNotNull(myCommunication)

    myProcessHandler = SbtShellTestUtil.acquireShellProcessHandler(project)

    myProcessListener = new SbtShellTestUtil.TestSbtShellProcessListener
    myProcessHandler.addProcessListener(myProcessListener)
  }

  override def dispose(): Unit =
    if (myProcessHandler != null && myProcessListener != null) {
      myProcessHandler.removeProcessListener(myProcessListener)
    }
}

object SbtShellTestFixture {
  val DefaultCommandWaitTimeout: FiniteDuration = 60.seconds
}
