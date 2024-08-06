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
}
