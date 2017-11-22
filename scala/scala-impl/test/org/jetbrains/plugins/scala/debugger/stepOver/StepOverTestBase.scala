package org.jetbrains.plugins.scala.debugger.stepOver

import org.jetbrains.plugins.scala.debugger.{ScalaDebuggerTestCase, ScalaPositionManager}
import org.jetbrains.plugins.scala.extensions._
import org.junit.Assert

import scala.io.Source

/**
 * @author Nikolay.Tropin
 */
abstract class StepOverTestBase extends ScalaDebuggerTestCase {
  def doStepOver(): Unit = {
    val stepOverCommand = getDebugProcess.createStepOverCommand(currentSuspendContext(), false)
    getDebugProcess.getManagerThread.invokeAndWait(stepOverCommand)
  }

  def testStepThrough(expectedLineNumbers: Seq[Int]): Unit = {
    val file = getFileInSrc(mainFileName)
    val lines = Source.fromFile(file).getLines().toSeq
    Assert.assertTrue(s"File should start with definition of object $mainClassName" , lines.head.startsWith(s"object $mainClassName"))

    def checkLine(expectedLineNumber: Int): Unit = {
      val actualLineNumber = currentLineNumber
      if (actualLineNumber != expectedLineNumber) {
        val message = {
          val actualLine = lines(actualLineNumber)
          val expectedLine = lines(expectedLineNumber)
          s"""Wrong line number.
              |Expected $expectedLineNumber: $expectedLine
              |Actual $actualLineNumber: $actualLine""".stripMargin
        }
        Assert.fail(message)
      }
    }

    val expectedNumbers = expectedLineNumbers.toIterator
    runDebugger(mainClassName) {
      while (!processTerminatedNoBreakpoints()) {
        if (expectedNumbers.hasNext) checkLine(expectedNumbers.next())
        else {
          val lineNumber = currentLineNumber
          Assert.fail(s"No expected lines left, stopped at line $lineNumber: ${lines(lineNumber)}")
        }
        doStepOver()
      }
    }
  }

  private def currentLineNumber: Int = {
    val location = currentLocation()
    inReadAction {
      positionManager.getSourcePosition(location).getLine
    }
  }
}
