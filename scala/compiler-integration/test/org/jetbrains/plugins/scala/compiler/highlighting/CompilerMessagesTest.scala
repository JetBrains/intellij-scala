package org.jetbrains.plugins.scala.compiler.highlighting

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.{DynamicTest, TestFactory}

class CompilerMessagesTest {

  private case class TestCase(displayName: String, originalMessage: String, expectedDescription: String)

  @TestFactory
  def compilerMessageTests(): Array[DynamicTest] =
    Array(
      TestCase(
        displayName = "sbtMultilineMessage",
        originalMessage = "Found:    Conversion.DoubleWrapper\nRequired: Int\n    override def apply(i: IntWrapper): Int = DoubleWrapper(i.a.toDouble)",
        expectedDescription = "Found:    Conversion.DoubleWrapper\nRequired: Int"),
      TestCase(
        displayName = "bspMultilineMessage",
        originalMessage = "Found:    Conversion.DoubleWrapper\nRequired: Int [14:46]",
        expectedDescription = "Found:    Conversion.DoubleWrapper\nRequired: Int"),
      TestCase(
        displayName = "deprecationWarningsMessage",
        originalMessage = "there were 4 deprecation warnings; re-run with -deprecation for details\n\n",
        expectedDescription = "there were 4 deprecation warnings; re-run with -deprecation for details"),
      TestCase(
        displayName = "oneLineMessage",
        originalMessage = "This is a one line error message",
        expectedDescription = "This is a one line error message"),
      TestCase(
        displayName = "blankAfterProcessing",
        originalMessage = "\n\nSome message  \n  ",
        expectedDescription = "Some message")
    ).map { case TestCase(displayName, originalMessage, expectedDescription) =>
      dynamicTest(displayName, () => assertMessageDescription(originalMessage, expectedDescription))
    }

  private def assertMessageDescription(originalMessage: String, expectedDescription: String): Unit = {
    val actualDescription = CompilerMessages.description(originalMessage)
    assertEquals(expectedDescription, actualDescription)
  }
}
