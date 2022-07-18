package org.jetbrains.plugins.scala
package lang.psi.impl.base.literals.escapers

import junit.framework.TestCase
import org.junit.Assert._
import org.junit.experimental.categories.Category

import java.lang

@Category(Array(classOf[LanguageTests]))
class ScalaStringParserTest extends TestCase {

  private def parse(content: String, isRaw: Boolean, exitOnEscapingWrongSymbol: Boolean = true): String = {
    val parser = new ScalaStringParser(null, isRaw, exitOnEscapingWrongSymbol)
    val builder = new lang.StringBuilder()
    parser.parse(content, builder)
    builder.toString
  }

  def testValidContent(): Unit = {
    val content = "X \\\\ X \t X \\t X \\\\t X \\u0023 X \\\\u0023 X"
    assertEquals("X \\ X \t X \t X \\t X # X \\u0023 X", parse(content, isRaw = false))
    //raw
    assertEquals("X \\\\ X \t X \\t X \\\\t X # X \\\\u0023 X", parse(content, isRaw = true))
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
    assertEquals("X X  X", parse(content, isRaw = false, exitOnEscapingWrongSymbol = false))
    //raw (raw content is not invalid actually)
    assertEquals("X \\ X \\j X", parse(content, isRaw = true, exitOnEscapingWrongSymbol = false))
  }
}