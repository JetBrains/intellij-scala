package org.jetbrains.plugins.scala.testingSupport

import com.intellij.execution.testframework.sm.runner.events.TestOutputEvent
import com.intellij.execution.testframework.sm.runner.{SMTRunnerEventsAdapter, SMTestProxy}
import com.intellij.openapi.util.Key

import scala.collection.mutable

class TestStatusListener extends SMTRunnerEventsAdapter {

  private val _uncapturedOutput: mutable.Buffer[(String, String, Key[_])] = mutable.ArrayBuffer.empty

  def uncapturedOutput: String = _uncapturedOutput
    .map { case (_, text, typ) => s"[$typ] $text" }
    .mkString

  override def onTestOutput(proxy: SMTestProxy, event: TestOutputEvent): Unit =
    super.onTestOutput(proxy, event)

  override def onUncapturedOutput(activeProxy: SMTestProxy, text: String, `type`: Key[_]): Unit = {
    val nodeName = activeProxy.getName
    _uncapturedOutput.append((nodeName, text, `type`))
  }
}