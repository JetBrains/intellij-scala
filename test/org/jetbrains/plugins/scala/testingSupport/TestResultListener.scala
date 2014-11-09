package org.jetbrains.plugins.scala
package testingSupport

import com.intellij.execution.process.{ProcessHandler, ProcessEvent, ProcessListener}
import com.intellij.openapi.util.Key
import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.execution.testframework.AbstractTestProxy

/**
* @author Roman.Shein
*         Date: 03.03.14
*/
class TestResultListener(private val testConfigurationName: String) extends ProcessListener {

  def waitForTestEnd(duration: Int): String = {
    var i = 0
    while (i < duration && (!terminated)) {
      Thread.sleep(10)
      i += 1
    }

    assert(terminated, "test " + testConfigurationName + " did not terminate correctly")
    builder.toString()
  }

  private val builder = new StringBuilder

  private var terminated = false

  override def onTextAvailable(event: ProcessEvent, outputType: Key[_]): Unit = {
    val text = event.getText
    import TestResultListener._
    if (text.contains(testResultPrefix) && text.contains(testResultSuffix)) {
      val from = text.indexOf(testResultPrefix)
      val to = text.indexOf(testResultSuffix)
      if (from != -1 && to != -1) {
        builder.append(text.substring(from + testResultPrefix.length, to))
      }
    }
  }

  override def processWillTerminate(event: ProcessEvent, willBeDestroyed: Boolean): Unit = {
      //TODO: implement me
  }

  override def processTerminated(event: ProcessEvent): Unit = {
    terminated = true
  }

  override def startNotified(event: ProcessEvent): Unit = {
    //TODO: implement me
  }
}

object TestResultListener {
  val testResultPrefix = ">>TEST: "
  val testResultSuffix = "<<"
}