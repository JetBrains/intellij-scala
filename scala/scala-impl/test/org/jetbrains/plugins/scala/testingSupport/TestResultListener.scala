package org.jetbrains.plugins.scala
package testingSupport

import com.intellij.execution.process.{ProcessEvent, ProcessListener}
import com.intellij.openapi.util.Key
import org.jetbrains.plugins.scala.testingSupport.TestResultListener._
import org.jetbrains.plugins.scala.util.assertions.failWithCause
import org.junit.Assert.fail

import scala.concurrent.{Await, Promise}
import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success, Try}


class TestResultListener(private val testConfigurationName: String) extends ProcessListener {

  private val outputTextAll = new StringBuilder
  private val outputTextProgress = new StringBuilder
  private val exitCodePromise: Promise[Int] = Promise()

  def waitForTestEnd(durationMs: Int): String = {
    val exitCode = Try(Await.result(exitCodePromise.future, durationMs.milliseconds))
    exitCode match {
      case Success(0)     =>
      case Success(code)     =>
        fail(
          s"""test $testConfigurationName terminated with error exit code: $code; captured outputs:
             |${outputTextAll.toString}""".stripMargin
        )
      case Failure(exception) =>
        failWithCause(
          s"""test $testConfigurationName did not terminate correctly after $durationMs ms; captured outputs:
             |${outputTextAll.toString}""".stripMargin,
          exception
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
    exitCodePromise.success(event.getExitCode)
}

private object TestResultListener {
  private val testResultPrefix = ">>TEST: "
  private val testResultSuffix = "<<"
}