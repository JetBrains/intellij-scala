package org.jetbrains.plugins.scala.lang.formatter.tests

import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import org.jetbrains.plugins.scala.lang.formatter.AbstractScalaFormatterTestBase

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
  def testInfixExpressionWrapAsNeeded() {
    getCommonSettings.BINARY_OPERATION_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED
    getSettings.setRightMargin(null, 20)
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

  def testInfixPatternWrapAsNeeded() {
    getCommonSettings.BINARY_OPERATION_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED
    getSettings.setRightMargin(null, 20)
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

  def testInfixTypeWrapAsNeeded() {
    getCommonSettings.BINARY_OPERATION_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED
    getSettings.setRightMargin(null, 20)
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

  def testInfixExprWrapAlways() {
    getCommonSettings.BINARY_OPERATION_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS
    getSettings.setRightMargin(null, 20)
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

  def testInfixExprWrapAllIfLong() {
    getCommonSettings.BINARY_OPERATION_WRAP = CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM
    getSettings.setRightMargin(null, 20)
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

  def testInfixExprDoNotWrap() {
    getCommonSettings.BINARY_OPERATION_WRAP = CommonCodeStyleSettings.DO_NOT_WRAP
    getSettings.setRightMargin(null, 20)
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

  def testAlignBinary() {
    getCommonSettings.ALIGN_MULTILINE_BINARY_OPERATION = true
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

  def testBinaryParentExpressionWrap() {
    getCommonSettings.BINARY_OPERATION_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED
    getCommonSettings.PARENTHESES_EXPRESSION_LPAREN_WRAP = true
    getCommonSettings.PARENTHESES_EXPRESSION_RPAREN_WRAP = true
    getSettings.setRightMargin(null, 20)
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

  def testCallParametersWrap() {
    getCommonSettings.CALL_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS
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

  def testAlignMultilineParametersCalls() {
    getCommonSettings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true
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

  def testCallParametersParen() {
    getCommonSettings.CALL_PARAMETERS_LPAREN_ON_NEXT_LINE = true
    getCommonSettings.CALL_PARAMETERS_RPAREN_ON_NEXT_LINE = true
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

  def testMethodCallChainWrap() {
    getCommonSettings.METHOD_CALL_CHAIN_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS
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

  def testMethodCallChainAlign() {
    getCommonSettings.ALIGN_MULTILINE_CHAINED_METHODS = true
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

  def testBraceStyle() {
    getCommonSettings.CLASS_BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE
    getCommonSettings.METHOD_BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE_SHIFTED
    getCommonSettings.BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE_IF_WRAPPED
    val before =
"""
class A {
  def foo = {
  val z =
  {
   3
  }
  }
}
class B extends A {
}
""".replace("\r", "")
    val after =
"""
class A
{
  def foo =
    {
    val z =
    {
      3
    }
    }
}

class B extends A
{
}
""".replace("\r", "")
    doTextTest(before, after)
  }

  def testChopDown() {
    getSettings.setRightMargin(null, 50)
    getIndentOptions.CONTINUATION_INDENT_SIZE = 2
    getCommonSettings.METHOD_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM
    getCommonSettings.METHOD_PARAMETERS_LPAREN_ON_NEXT_LINE = true
    getCommonSettings.METHOD_PARAMETERS_RPAREN_ON_NEXT_LINE = true
    getScalaSettings.NOT_CONTINUATION_INDENT_FOR_PARAMS = true
    val before =
""" 
class Foo {
  def methodWithTooManyParamsForALine(param1: Bar, param2: Bar, param3: Bar, param4: Bar, param5: Bar, param6: Bar) = {
    // some code
  }
}
""".replace("\r", "")
    val after =
"""
class Foo {
  def methodWithTooManyParamsForALine(
    param1: Bar,
    param2: Bar,
    param3: Bar,
    param4: Bar,
    param5: Bar,
    param6: Bar
  ) = {
    // some code
  }
}
""".replace("\r", "")
    doTextTest(before, after)
  }
}