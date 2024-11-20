package org.jetbrains.plugins.scala.compiler.highlighting

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CompilerMessagesTest {

  @Test
  def sbtMultilineMessage(): Unit = {
    val originalMessage = "Found:    Conversion.DoubleWrapper\nRequired: Int\n    override def apply(i: IntWrapper): Int = DoubleWrapper(i.a.toDouble)"
    val expected = "Found:    Conversion.DoubleWrapper\nRequired: Int"
    val actual = CompilerMessages.description(originalMessage)
    assertEquals(expected, actual)
  }

  @Test
  def bspMultilineMessage(): Unit = {
    val originalMessage = "Found:    Conversion.DoubleWrapper\nRequired: Int [14:46]"
    val expected = "Found:    Conversion.DoubleWrapper\nRequired: Int"
    val actual = CompilerMessages.description(originalMessage)
    assertEquals(expected, actual)
  }

  @Test
  def deprecationWarningsMessage(): Unit = {
    val originalMessage = "there were 4 deprecation warnings; re-run with -deprecation for details\n\n"
    val expected = "there were 4 deprecation warnings; re-run with -deprecation for details"
    val actual = CompilerMessages.description(originalMessage)
    assertEquals(expected, actual)
  }

  @Test
  def oneLineMessage(): Unit = {
    val originalMessage = "This is a one line error message"
    val actual = CompilerMessages.description(originalMessage)
    assertEquals(originalMessage, actual)
  }

  @Test
  def blankAfterProcessing(): Unit = {
    val originalMessage = "\n\nSome message  \n  "
    val expected = "Some message"
    val actual = CompilerMessages.description(originalMessage)
    assertEquals(expected, actual)
  }
}
