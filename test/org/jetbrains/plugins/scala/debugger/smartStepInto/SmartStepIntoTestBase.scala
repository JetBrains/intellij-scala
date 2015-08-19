package org.jetbrains.plugins.scala.debugger.smartStepInto

import com.intellij.debugger.actions.SmartStepTarget
import org.jetbrains.plugins.scala.debugger.ScalaDebuggerTestCase
import org.jetbrains.plugins.scala.extensions.inReadAction
import org.junit.Assert

import scala.collection.JavaConverters._

/**
 * @author Nikolay.Tropin
 */
abstract class SmartStepIntoTestBase extends ScalaDebuggerTestCase {

  protected val handler = new ScalaSmartStepIntoHandler
  protected var targets: Seq[SmartStepTarget] = null

  def availableSmartStepTargets(): Seq[SmartStepTarget] = managed {
    inReadAction {
      handler.findSmartStepTargets(currentSourcePosition).asScala
    }
  }

  def checkSmartStepTargets(expected: String*): Unit = {
    targets = availableSmartStepTargets()
    Assert.assertEquals("Wrong set of smart step targets:", expected, targets.map(_.getPresentation))
  }

  def checkSmartStepInto(target: String, source: String, methodName: String, line: Int) = {
    if (targets == null) targets = availableSmartStepTargets()
    val sst = targets.find(_.getPresentation == target)
    Assert.assertTrue(s"Cannot find such target: $target", sst.isDefined)
    doSmartStepInto(sst.get)
    checkLocation(source, methodName, line)
  }

  private def doSmartStepInto(target: SmartStepTarget): Unit = {
    val filter = handler.createMethodFilter(target)
    val stepIntoCommand = getDebugProcess.createStepIntoCommand(suspendContext, false, filter)
    getDebugProcess.getManagerThread.invokeAndWait(stepIntoCommand)
    waitForBreakpoint()
  }
}