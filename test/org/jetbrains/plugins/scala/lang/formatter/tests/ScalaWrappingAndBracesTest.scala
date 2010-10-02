package org.jetbrains.plugins.scala.lang.formatter.tests

import org.jetbrains.plugins.scala.lang.formatter.AbstractScalaFormatterTest
import com.intellij.psi.codeStyle.CommonCodeStyleSettings

/**
 * @author Alexander Podkhalyuzin
 */

class ScalaWrappingAndBracesTest extends AbstractScalaFormatterTest {
  def testInfixExpressionWrapAsNeeded {
    getScalaSettings.SCALA_BINARY_OPERATION_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED
    getSettings.RIGHT_MARGIN = 20
    getIndentOptions.CONTINUATION_INDENT_SIZE = 2
    val before =
"""
2 + 2 + 2 + 2 + 2 + 2 + 2 + 2 + 2 + 2 + 2 + 2 + 2 + 2 + 2
2 :: 2 :: 2 :: 2 :: 2 :: 2 :: 2 :: 2 :: 2 :: 2 :: 2 :: 2 :: Nil
2 + 2 + 2 + 2 + 22 * 66 + 2
""".replace("\r", "")
    val after =
"""
2 + 2 + 2 + 2 + 2 +
  2 + 2 + 2 + 2 +
  2 + 2 + 2 + 2 +
  2 + 2
2 :: 2 :: 2 :: 2 ::
  2 :: 2 :: 2 ::
  2 :: 2 :: 2 ::
  2 :: 2 :: Nil
2 + 2 + 2 + 2 +
  22 * 66 + 2
""".replace("\r", "")
    doTextTest(before, after)
  }

  def testInfixPatternWrapAsNeeded {
    getScalaSettings.SCALA_BINARY_OPERATION_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED
    getSettings.RIGHT_MARGIN = 20
    getIndentOptions.CONTINUATION_INDENT_SIZE = 2
    val before =
"""
List(1, 2) match {
  case x :: y :: z :: Nil =>
}
""".replace("\r", "")
    val after =
"""
List(1, 2) match {
  case x :: y ::
    z :: Nil =>
}
""".replace("\r", "")
    doTextTest(before, after)
  }

  def testInfixTypeWrapAsNeeded {
    getScalaSettings.SCALA_BINARY_OPERATION_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED
    getSettings.RIGHT_MARGIN = 20
    getIndentOptions.CONTINUATION_INDENT_SIZE = 2
    val before =
"""
val x: T + T + T + T + T
""".replace("\r", "")
    val after =
"""
val x: T + T + T +
  T + T
""".replace("\r", "")
    doTextTest(before, after)
  }

  def testInfixExprWrapAlways {
    getScalaSettings.SCALA_BINARY_OPERATION_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS
    getSettings.RIGHT_MARGIN = 20
    getIndentOptions.CONTINUATION_INDENT_SIZE = 2
    val before =
"""
2 + 3 + 4 * 6 + (7 + 9 * 10) - 8 - 4
""".replace("\r", "")
    val after =
"""

""".replace("\r", "")
    doTextTest(before, after)
  }

  def testInfixExprWrapAllIfLong {
    getScalaSettings.SCALA_BINARY_OPERATION_WRAP = CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM
    getSettings.RIGHT_MARGIN = 20
    getIndentOptions.CONTINUATION_INDENT_SIZE = 2
    val before =
"""
2 + 2 + 2 + 2 + 2 + 2
2 + 2 + 2 + 2 + 2
""".replace("\r", "")
    val after =
"""

""".replace("\r", "")
    doTextTest(before, after)
  }

  def testInfixExprDoNotWrap {
    getScalaSettings.SCALA_BINARY_OPERATION_WRAP = CommonCodeStyleSettings.DO_NOT_WRAP
    getSettings.RIGHT_MARGIN = 20
    getIndentOptions.CONTINUATION_INDENT_SIZE = 2
    val before =
"""
2 + 2 + 2 + 2 + 2 + 2
2 + 2 + 2 + 2 + 2
""".replace("\r", "")
    val after =
"""
2 + 2 + 2 + 2 + 2 + 2
2 + 2 + 2 + 2 + 2
""".replace("\r", "")
    doTextTest(before, after)
  }
}