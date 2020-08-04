package org.jetbrains.plugins.scala.testingSupport

import com.intellij.execution.testframework.sm.runner.{SMTRunnerEventsAdapter, SMTestProxy}
import com.intellij.openapi.util.Key

import scala.collection.mutable

class TestStatusListener extends SMTRunnerEventsAdapter {

  private val _uncapturedOutput: mutable.Buffer[(String, Key[_])] = mutable.ArrayBuffer.empty

  def uncapturedOutput: String = _uncapturedOutput
    .map { case (text, typ) => s"[$typ] $text" }
    .mkString

  override def onUncapturedOutput(activeProxy: SMTestProxy, text: String, `type`: Key[_]): Unit =
    _uncapturedOutput.append((text, `type`))
}