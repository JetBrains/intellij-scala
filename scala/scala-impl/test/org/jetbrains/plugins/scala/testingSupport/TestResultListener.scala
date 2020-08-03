package org.jetbrains.plugins.scala
package testingSupport

import com.intellij.execution.process.{ProcessEvent, ProcessListener}
import com.intellij.openapi.util.Key
import org.jetbrains.plugins.scala.testingSupport.TestResultListener._

import scala.concurrent.Promise


class TestResultListener extends ProcessListener {

  private val _outputTextAll = new StringBuilder
  private val _outputTextProgress = new StringBuilder
  val exitCodePromise: Promise[Int] = Promise()

  def outputText: String = _outputTextAll.mkString
  def outputTextProgress: String = _outputTextProgress.mkString

  override def onTextAvailable(event: ProcessEvent, outputType: Key[_]): Unit = {
    val text = event.getText
    _outputTextAll.append(text)

    if (text.contains(testResultPrefix) && text.contains(testResultSuffix)) {
      val from = text.indexOf(testResultPrefix)
      val to = text.indexOf(testResultSuffix)
      if (from != -1 && to != -1) {
        _outputTextProgress.append(text.substring(from + testResultPrefix.length, to))
      }
    }
  }

  override def startNotified(event: ProcessEvent): Unit = ()

  override def processWillTerminate(event: ProcessEvent, willBeDestroyed: Boolean): Unit = ()

  override def processTerminated(event: ProcessEvent): Unit =
    exitCodePromise.success(event.getExitCode)
}

private object TestResultListener {
  private val testResultPrefix = ">>TEST: "
  private val testResultSuffix = "<<"
}