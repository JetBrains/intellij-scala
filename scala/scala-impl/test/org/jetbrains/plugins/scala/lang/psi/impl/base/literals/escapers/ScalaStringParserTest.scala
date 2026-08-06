package org.jetbrains.plugins.scala.lang.psi.impl.base.literals.escapers

import junit.framework.TestCase
import org.junit.Assert._

import scala.annotation.nowarn

//noinspection RedundantDefaultArgument
class ScalaStringParserTest extends TestCase {

  private def parse(
    content: String,
    isRaw: Boolean,
    noUnicodeEscapesInRawStrings: Boolean = false,
    exitOnEscapingWrongSymbol: Boolean = true,
  ): String = ScalaStringParser.unescape(content, isRaw, noUnicodeEscapesInRawStrings, exitOnEscapingWrongSymbol)

  def testValidContent(): Unit = {
    val content = "X \\\\ X \t X \\t X \\\\t X \\u0023 X \\\\u0023 X"
    assertEquals("X \\ X \t X \t X \\t X # X \\u0023 X", parse(content, isRaw = false))
    //raw
    assertEquals("X \\\\ X \t X \\t X \\\\t X # X \\\\u0023 X", parse(content, isRaw = true))
  }

  def testValidContent_NoUnicodeSequenceInRaw(): Unit = {
    val content = "X \\\\ X \t X \\t X \\\\t X \\u0023 X \\\\u0023 X"
    assertEquals("X \\\\ X \t X \\t X \\\\t X \\u0023 X \\\\u0023 X", parse(content, isRaw = true, noUnicodeEscapesInRawStrings = true))
  }

  def testStopAtInvalidEscape(): Unit = {
    val content = "X \\\\ X \\t X \\ X \\j X"
    assertEquals("X \\ X \t X ", parse(content, isRaw = false))
    //raw (raw content is not invalid actually)
    assertEquals("X \\\\ X \\t X \\ X \\j X", parse(content, isRaw = true))
  }

  // ignore invalid escapes
  def testDontStopAtInvalidEscape(): Unit = {
    val content = "X \\ X \\j X"
    assertEquals("X  X   X", parse(content, isRaw = false, exitOnEscapingWrongSymbol = false))
    //raw (raw content is not invalid actually)
    assertEquals("X \\ X \\j X", parse(content, isRaw = true, exitOnEscapingWrongSymbol = false))
  }

  @
  nowarn("cat=deprecation")
  private val CommonInnerStringContent_AllEscapesInOne = """aaa \b bbb \f ccc \n ddd \r eee \t fff \' ggg \\ hhh \\u0024 eee \u005cu0024 fff"""

  //TODO: once SCL-25152 is fixed add it as well
  def testEscapeSequencesAllInOne_Plain(): Unit = {
    assertEquals(
      "aaa \b bbb \f ccc \n ddd \r eee \t fff ' ggg \\ hhh \\u0024 eee $ fff",
      parse(CommonInnerStringContent_AllEscapesInOne, isRaw = false, noUnicodeEscapesInRawStrings = false, exitOnEscapingWrongSymbol = false)
    )
  }

  def testEscapeSequencesAllInOne_Raw(): Unit = {
    assertEquals(
      "aaa \\b bbb \\f ccc \\n ddd \\r eee \\t fff \\' ggg \\\\ hhh \\\\u0024 eee $ fff",
      parse(CommonInnerStringContent_AllEscapesInOne, isRaw = true, noUnicodeEscapesInRawStrings = false, exitOnEscapingWrongSymbol = false)
    )
  }

  def testEscapeSequencesAllInOne_Plain_NoUnicode(): Unit = {
    assertEquals(
      "aaa \b bbb \f ccc \n ddd \r eee \t fff ' ggg \\ hhh \\u0024 eee $ fff",
      parse(CommonInnerStringContent_AllEscapesInOne, isRaw = false, noUnicodeEscapesInRawStrings = true, exitOnEscapingWrongSymbol = false)
    )
  }

  def testEscapeSequencesAllInOne_Raw_NoUnicode(): Unit = {
    assertEquals(
      "aaa \\b bbb \\f ccc \\n ddd \\r eee \\t fff \\' ggg \\\\ hhh \\\\u0024 eee \\u0024 fff",
      parse(CommonInnerStringContent_AllEscapesInOne, isRaw = true, noUnicodeEscapesInRawStrings = true, exitOnEscapingWrongSymbol = false)
    )
  }
}
