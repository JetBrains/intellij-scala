package org.jetbrains.plugins.scala.compiler.highlighting

import junitparams.naming.TestCaseName
import junitparams.{JUnitParamsRunner, Parameters}
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

import scala.annotation.unused

@RunWith(classOf[JUnitParamsRunner])
class CompilerMessagesTest {

  case class TestCaseParams(displayName: String, originalMessage: String, expectedDescription: String) {
    override def toString: String = displayName
  }

  @unused("used reflectively by the @Parameters annotation")
  private def testParameters: Array[AnyRef] = Array(
    TestCaseParams(
      displayName = "sbtMultilineMessage",
      originalMessage = "Found:    Conversion.DoubleWrapper\nRequired: Int\n    override def apply(i: IntWrapper): Int = DoubleWrapper(i.a.toDouble)",
      expectedDescription = "Found:    Conversion.DoubleWrapper\nRequired: Int"),
    TestCaseParams(
      displayName = "bspMultilineMessage",
      originalMessage = "Found:    Conversion.DoubleWrapper\nRequired: Int [14:46]",
      expectedDescription = "Found:    Conversion.DoubleWrapper\nRequired: Int"),
    TestCaseParams(
      displayName = "deprecationWarningsMessage",
      originalMessage = "there were 4 deprecation warnings; re-run with -deprecation for details\n\n",
      expectedDescription = "there were 4 deprecation warnings; re-run with -deprecation for details"),
    TestCaseParams(
      displayName = "oneLineMessage",
      originalMessage = "This is a one line error message",
      expectedDescription = "This is a one line error message"),
    TestCaseParams(
      displayName = "blankAfterProcessing",
      originalMessage = "\n\nSome message  \n  ",
      expectedDescription = "Some message")
  )

  @Test
  @Parameters(method = "testParameters")
  @TestCaseName(value = "{method}[{0}]")
  def compilerMessageTest(params: TestCaseParams): Unit = {
    val TestCaseParams(_, originalMessage, expectedDescription) = params
    assertMessageDescription(originalMessage, expectedDescription)
  }

  private def assertMessageDescription(originalMessage: String, expectedDescription: String): Unit = {
    val actualDescription = CompilerMessages.description(originalMessage)
    assertEquals(expectedDescription, actualDescription)
  }
}
