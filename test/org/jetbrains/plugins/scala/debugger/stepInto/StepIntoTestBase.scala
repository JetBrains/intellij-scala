package org.jetbrains.plugins.scala.debugger.stepInto

import org.jetbrains.plugins.scala.debugger.ScalaDebuggerTestCase

/**
 * @author Nikolay.Tropin
 */
abstract class StepIntoTestBase extends ScalaDebuggerTestCase {
  def doStepInto(): Unit = {
    val stepIntoCommand = getDebugProcess.createStepIntoCommand(suspendContext, false, null)
    getDebugProcess.getManagerThread.invokeAndWait(stepIntoCommand)
    waitForBreakpoint()
  }
}