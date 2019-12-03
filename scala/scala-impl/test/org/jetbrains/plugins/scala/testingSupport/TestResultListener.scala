package org.jetbrains.plugins.scala
package testingSupport

import com.intellij.execution.process.{ProcessEvent, ProcessListener}
import com.intellij.openapi.util.Key
import org.jetbrains.plugins.scala.testingSupport.TestResultListener._
import org.junit.Assert._


class TestResultListener(private val testConfigurationName: String) extends ProcessListener {

  private val outputTextAll = new StringBuilder
  private val outputTextProgress = new StringBuilder

  private def terminated = exitCodeOpt.isDefined
  private var exitCodeOpt: Option[Int] = None

  def waitForTestEnd(duration: Int): String = {
    var i = 0
    while (i < duration && (!terminated)) {
      Thread.sleep(10)
      i += 10
    }

    exitCodeOpt match {
      case Some(0) =>
      case Some(exitCode) =>
        fail(
          s"""test $testConfigurationName terminated with error exit code: $exitCode; captured outputs:
             |${outputTextAll.toString}""".stripMargin
        )
      case None =>
        fail(
          s"""test $testConfigurationName did not terminate correctly after $duration ms; captured outputs:
             |${outputTextAll.toString}""".stripMargin
        )
    }
    outputTextProgress.toString
  }

  override def onTextAvailable(event: ProcessEvent, outputType: Key[_]): Unit = {
    val text = event.getText
    outputTextAll.append(text)

    if (text.contains(testResultPrefix) && text.contains(testResultSuffix)) {
      val from = text.indexOf(testResultPrefix)
      val to = text.indexOf(testResultSuffix)
      if (from != -1 && to != -1) {
        outputTextProgress.append(text.substring(from + testResultPrefix.length, to))
      }
    }
  }

  override def startNotified(event: ProcessEvent): Unit = ()

  override def processWillTerminate(event: ProcessEvent, willBeDestroyed: Boolean): Unit = ()

  override def processTerminated(event: ProcessEvent): Unit =
    exitCodeOpt = Some(event.getExitCode)
}

private object TestResultListener {
  private val testResultPrefix = ">>TEST: "
  private val testResultSuffix = "<<"
}