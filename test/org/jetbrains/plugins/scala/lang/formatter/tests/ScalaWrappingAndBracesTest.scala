package org.jetbrains.plugins.scala.lang.formatter.tests

import org.jetbrains.plugins.scala.lang.formatter.AbstractScalaFormatterTestBase
import com.intellij.psi.codeStyle.CommonCodeStyleSettings

/**
 * @author Alexander Podkhalyuzin
 */

class ScalaWrappingAndBracesTest extends AbstractScalaFormatterTestBase {
  /* stub:
  def test {
    val before =
"""
""".replace("\r", "")
    val after =
"""
""".replace("\r", "")
    doTextTest(before, after)
  }
   */
  def testInfixExpressionWrapAsNeeded {
    getSettings.BINARY_OPERATION_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED
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
    getSettings.BINARY_OPERATION_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED
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
    getSettings.BINARY_OPERATION_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED
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
    getSettings.BINARY_OPERATION_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS
    getSettings.RIGHT_MARGIN = 20
    getIndentOptions.CONTINUATION_INDENT_SIZE = 2
    val before =
"""
2 + 3 + 4 * 6 + (7 + 9 * 10) - 8 - 4
""".replace("\r", "")
    val after =
"""
2 +
  3 +
  4 *
    6 +
  (7 +
    9 *
      10) -
  8 -
  4
""".replace("\r", "")
    doTextTest(before, after)
  }

  def testInfixExprWrapAllIfLong {
    getSettings.BINARY_OPERATION_WRAP = CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM
    getSettings.RIGHT_MARGIN = 20
    getIndentOptions.CONTINUATION_INDENT_SIZE = 2
    val before =
"""
2 + 2 + 2 + 2 + 2 + 2
2 + 2 + 2 + 2 + 2
""".replace("\r", "")
    val after =
"""
2 +
  2 +
  2 +
  2 +
  2 +
  2
2 + 2 + 2 + 2 + 2
""".replace("\r", "")
    doTextTest(before, after)
  }

  def testInfixExprDoNotWrap {
    getSettings.BINARY_OPERATION_WRAP = CommonCodeStyleSettings.DO_NOT_WRAP
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

  def testAlignBinary {
    getSettings.ALIGN_MULTILINE_BINARY_OPERATION = true
    val before =
"""
val i = 2 + 2 +
 3 + 5 +
 6 + 7 *
 8
""".replace("\r", "")
    val after =
"""
val i = 2 + 2 +
        3 + 5 +
        6 + 7 *
            8
""".replace("\r", "")
    doTextTest(before, after)
  }

  def testBinaryParentExpressionWrap {
    getSettings.BINARY_OPERATION_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED
    getSettings.PARENTHESES_EXPRESSION_LPAREN_WRAP = true
    getSettings.PARENTHESES_EXPRESSION_RPAREN_WRAP = true
    getSettings.RIGHT_MARGIN = 20
    val before =
"""
(2333333333333333 + 2)
(2 +
2)
""".replace("\r", "")
    val after =
"""
(
  2333333333333333 +
    2
  )
(
  2 +
    2
  )
""".replace("\r", "")
    doTextTest(before, after)
  }

  def testCallParametersWrap {
    getSettings.CALL_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS
    val before =
"""
foo(1, 2, 3)
""".replace("\r", "")
    val after =
"""
foo(1,
  2,
  3)
""".replace("\r", "")
    doTextTest(before, after)
  }

  def testAlignMultilineParametersCalls {
    getSettings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true
    val before =
"""
foo(1,
2,
3)
""".replace("\r", "")
    val after =
"""
foo(1,
    2,
    3)
""".replace("\r", "")
    doTextTest(before, after)
  }

  def testCallParametersParen {
    getSettings.CALL_PARAMETERS_LPAREN_ON_NEXT_LINE = true
    getSettings.CALL_PARAMETERS_RPAREN_ON_NEXT_LINE = true
    val before =
"""
foo(1,
2,
3)
""".replace("\r", "")
    val after =
"""
foo(
  1,
  2,
  3
)
""".replace("\r", "")
    doTextTest(before, after)
  }

  def testMethodCallChainWrap {
    getSettings.METHOD_CALL_CHAIN_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS
    val before =
"""
foo(1, 2).foo(1, 2).foo(1, 2)
""".replace("\r", "")
    val after =
"""
foo(1, 2)
  .foo(1, 2)
  .foo(1, 2)
""".replace("\r", "")
    doTextTest(before, after)
  }

  def testMethodCallChainAlign {
    getSettings.ALIGN_MULTILINE_CHAINED_METHODS = true
    val before =
"""
val x = foo.
foo.goo.
foo(1, 2, 3).
foo.
foo
.foo
""".replace("\r", "")
    val after =
"""
val x = foo.
        foo.goo.
        foo(1, 2, 3).
        foo.
        foo
        .foo
""".replace("\r", "")
    doTextTest(before, after)
  }

}