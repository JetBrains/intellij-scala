package org.jetbrains.plugins.scala
package codeInspection.format

import base.SimpleTestCase
import codeInspection.format.Format._
import lang.psi.impl.ScalaPsiElementFactory
import com.intellij.psi.PsiManager
import org.junit.Assert._

/**
 * Pavel Fatin
 */

class InterpolatedStringFormattingTest extends SimpleTestCase {
  def testEmpty() {
    assertEquals("", format(""))
  }

  def testText() {
    assertEquals("foo", format("foo"))
  }

  def testUnformattedExpression() {
    assertEquals("$exp", format("%d", "exp"))
  }

  def testUnformattedBlockExpression() {
    assertEquals("${exp.name}", format("%d", "exp.name"))
  }

  def testFormattedExpression() {
    assertEquals("$exp%2d", format("%2d", "exp"))
  }

  def testFormattedBlockExpression() {
    assertEquals("${exp.name}%2d", format("%2d", "exp.name"))
  }

  def testMixed() {
    assertEquals("foo ${exp.name}%2d bar", format("foo %2d bar", "exp.name"))
  }

  def testLiterals() {
    assertEquals("1", format("%s", "0x1"))
    assertEquals("foo", format("%s", "\"foo\""))
  }

  private def format(formatString: String, arguments: String*): String = {
    val parts = parse(formatString, arguments: _*)
    Format.formatAsInterpolatedString(parts)
  }

  private def parse(formatString: String, arguments: String*): List[Part] = {
    val manager = PsiManager.getInstance(fixture.getProject)
    val elements = arguments.map(it => ScalaPsiElementFactory.createExpressionFromText(it.toString, manager))
    parseFormatCall(formatString, elements).toList
  }
}
