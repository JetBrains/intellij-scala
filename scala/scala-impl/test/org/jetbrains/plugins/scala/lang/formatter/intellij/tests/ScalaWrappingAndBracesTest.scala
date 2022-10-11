package org.jetbrains.plugins.scala.lang.formatter.intellij.tests

import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import org.jetbrains.plugins.scala.ScalaLanguage
import org.jetbrains.plugins.scala.lang.formatter.AbstractScalaFormatterTestBase
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.junit.Ignore

class ScalaWrappingAndBracesTest extends AbstractScalaFormatterTestBase {
  private val RightMarginMarker = "!"
  private val CHOP_DOWN_IF_LONG = CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM

  private def setupRightMargin(rightMarginVisualHelper: String): Unit = {
    getSettings.setRightMargin(ScalaLanguage.INSTANCE, rightMarginVisualHelper.indexOf(RightMarginMarker))
  }

  def testInfixExpressionWrapAsNeeded(): Unit = {
    getCommonSettings.BINARY_OPERATION_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED
    getSettings.setRightMargin(null, 20)
    getIndentOptions.CONTINUATION_INDENT_SIZE = 2
    val before =
      """2 + 2 + 2 + 2 + 2 + 2 + 2 + 2 + 2 + 2 + 2 + 2 + 2 + 2 + 2
        |2 :: 2 :: 2 :: 2 :: 2 :: 2 :: 2 :: 2 :: 2 :: 2 :: 2 :: 2 :: Nil
        |2 + 2 + 2 + 2 + 22 * 66 + 2
        |""".stripMargin
    val after =
      """2 + 2 + 2 + 2 + 2 +
        |  2 + 2 + 2 + 2 +
        |  2 + 2 + 2 + 2 +
        |  2 + 2
        |2 :: 2 :: 2 :: 2 ::
        |  2 :: 2 :: 2 ::
        |  2 :: 2 :: 2 ::
        |  2 :: 2 :: Nil
        |2 + 2 + 2 + 2 +
        |  22 * 66 + 2
        |""".stripMargin
    doTextTest(before, after)
  }

  def testInfixPatternWrapAsNeeded(): Unit = {
    getCommonSettings.BINARY_OPERATION_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED
    getSettings.setRightMargin(null, 20)
    getIndentOptions.CONTINUATION_INDENT_SIZE = 2
    val before =
      """List(1, 2) match {
        |  case x :: y :: z :: Nil =>
        |}
        |""".stripMargin
    val after =
      """List(1, 2) match {
        |  case x :: y ::
        |    z :: Nil =>
        |}
        |""".stripMargin
    doTextTest(before, after)
  }

  def testInfixTypeWrapAsNeeded(): Unit = {
    getCommonSettings.BINARY_OPERATION_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED
    getSettings.setRightMargin(null, 20)
    getIndentOptions.CONTINUATION_INDENT_SIZE = 2
    val before =
      """val x: T + T + T + T + T
        |""".stripMargin
    val after =
      """val x: T + T + T +
        |  T + T
        |""".stripMargin
    doTextTest(before, after)
  }

  def testInfixExprWrapAlways(): Unit = {
    getCommonSettings.BINARY_OPERATION_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS
    getSettings.setRightMargin(null, 20)
    getIndentOptions.CONTINUATION_INDENT_SIZE = 2
    val before =
      """2 + 3 + 4 * 6 + (7 + 9 * 10) - 8 - 4
        |""".stripMargin
    val after =
      """2 +
        |  3 +
        |  4 *
        |    6 +
        |  (7 +
        |    9 *
        |      10) -
        |  8 -
        |  4
        |""".stripMargin
    doTextTest(before, after)
  }

  def testInfixExprWrapAllIfLong(): Unit = {
    getCommonSettings.BINARY_OPERATION_WRAP = CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM
    getSettings.setRightMargin(null, 20)
    getIndentOptions.CONTINUATION_INDENT_SIZE = 2
    val before =
      """2 + 2 + 2 + 2 + 2 + 2
        |2 + 2 + 2 + 2 + 2
        |""".stripMargin
    val after =
      """2 +
        |  2 +
        |  2 +
        |  2 +
        |  2 +
        |  2
        |2 + 2 + 2 + 2 + 2
        |""".stripMargin
    doTextTest(before, after)
  }

  def testInfixExprDoNotWrap(): Unit = {
    getCommonSettings.BINARY_OPERATION_WRAP = CommonCodeStyleSettings.DO_NOT_WRAP
    getSettings.setRightMargin(null, 20)
    getIndentOptions.CONTINUATION_INDENT_SIZE = 2
    val before =
      """2 + 2 + 2 + 2 + 2 + 2
        |2 + 2 + 2 + 2 + 2
        |""".stripMargin
    val after =
      """2 + 2 + 2 + 2 + 2 + 2
        |2 + 2 + 2 + 2 + 2
        |""".stripMargin
    doTextTest(before, after)
  }

  def testAlignBinary(): Unit = {
    getCommonSettings.ALIGN_MULTILINE_BINARY_OPERATION = true
    val before =
      """val i = 2 + 2 +
        | 3 + 5 +
        | 6 + 7 *
        | 8
        |""".stripMargin
    val after =
      """val i = 2 + 2 +
        |        3 + 5 +
        |        6 + 7 *
        |            8
        |""".stripMargin
    doTextTest(before, after)
  }

  def testBinaryParentExpressionWrap(): Unit = {
    getCommonSettings.BINARY_OPERATION_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED
    getCommonSettings.PARENTHESES_EXPRESSION_LPAREN_WRAP = true
    getCommonSettings.PARENTHESES_EXPRESSION_RPAREN_WRAP = true
    getSettings.setRightMargin(null, 20)
    val before =
      """(2333333333333333 + 2)
        |(2 +
        |2)
        |""".stripMargin
    val after =
      """(
        |  2333333333333333 +
        |    2
        |  )
        |(
        |  2 +
        |    2
        |  )
        |""".stripMargin
    doTextTest(before, after)
  }

  def testCallParametersWrap(): Unit = {
    getCommonSettings.CALL_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS
    val before =
      """foo(1, 2, 3)
        |""".stripMargin
    val after =
      """foo(1,
        |  2,
        |  3)
        |""".stripMargin
    doTextTest(before, after)
  }

  def testAlignMultilineParametersCalls(): Unit = {
    getCommonSettings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true
    val before =
      """foo(1,
        |2,
        |3)
        |""".stripMargin
    val after =
      """foo(1,
        |    2,
        |    3)
        |""".stripMargin
    doTextTest(before, after)
  }

  def testCallParametersParen(): Unit = {
    getScalaSettings.CALL_PARAMETERS_NEW_LINE_AFTER_LPAREN = ScalaCodeStyleSettings.NEW_LINE_ALWAYS
    getCommonSettings.CALL_PARAMETERS_RPAREN_ON_NEXT_LINE = true
    val before =
      """foo(1,
        |2,
        |3)
        |""".stripMargin
    val after =
      """foo(
        |  1,
        |  2,
        |  3
        |)
        |""".stripMargin
    doTextTest(before, after)
  }

  def testBraceStyle(): Unit = {
    getCommonSettings.CLASS_BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE
    getCommonSettings.METHOD_BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE_SHIFTED
    getCommonSettings.BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE_IF_WRAPPED
    val before =
      """class A {
        |  def foo = {
        |  val z =
        |  {
        |   3
        |  }
        |  }
        |}
        |class B extends A {
        |}
        |""".stripMargin
    val after =
      """class A
        |{
        |  def foo =
        |    {
        |    val z =
        |    {
        |      3
        |    }
        |    }
        |}
        |
        |class B extends A
        |{
        |}
        |""".stripMargin
    doTextTest(before, after)
  }


  //
  // Chained method calls
  //
  // Reminder about settings combinations:
  // (vertical align / do not align)
  // (no wrap / wrap if long / chop if long / wrap always )
  // (wrap first / no wrap first) # only for "wrap always" and "chop if long"

  //
  // Do not align when multiline
  //

  def test_MethodCallChain_DoNotWrap(): Unit = {
    getCommonSettings.METHOD_CALL_CHAIN_WRAP = CommonCodeStyleSettings.DO_NOT_WRAP
    setupRightMargin(
      """                               ! """)
    val before =
      """myObject.foo(1, 2).bar(3, 4).baz(5, 6).foo(7, 8).bar(9, 10).baz(11, 12)"""
    doTextTest(before)
  }

  def test_MethodCallChain_DoNotWrap_1(): Unit = {
    getCommonSettings.METHOD_CALL_CHAIN_WRAP = CommonCodeStyleSettings.DO_NOT_WRAP
    setupRightMargin(
      """                               ! """)
    val before =
      """myObject.foo(1, 2)
        |.bar(3, 4)
        |.baz(5, 6).foo(7, 8)""".stripMargin
    val after =
      """myObject.foo(1, 2)
        |  .bar(3, 4)
        |  .baz(5, 6).foo(7, 8)""".stripMargin
    doTextTest(before, after)
  }

  def test_MethodCallChain_WrapIfLong(): Unit = {
    getCommonSettings.METHOD_CALL_CHAIN_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED
    setupRightMargin(
      """                               ! """)
    val before =
      """myObject.foo(1, 2).bar(3, 4).baz(5, 6).foo(7, 8).bar(9, 10).baz(11, 12)
        |""".stripMargin
    val after =
      """myObject.foo(1, 2).bar(3, 4)
        |  .baz(5, 6).foo(7, 8)
        |  .bar(9, 10).baz(11, 12)
        |""".stripMargin
    doTextTest(before, after)
  }

  def test_MethodCallChain_ChopDownIfLong(): Unit = {
    getCommonSettings.METHOD_CALL_CHAIN_WRAP = CHOP_DOWN_IF_LONG
    setupRightMargin(
      """                               ! """)
    val before =
      """myObject.foo(1, 2).bar(3, 4).baz(5, 6).foo(7, 8).bar(9, 10).baz(11, 12)
        |""".stripMargin
    val after =
      """myObject.foo(1, 2)
        |  .bar(3, 4)
        |  .baz(5, 6)
        |  .foo(7, 8)
        |  .bar(9, 10)
        |  .baz(11, 12)
        |""".stripMargin
    doTextTest(before, after)
  }

  def test_MethodCallChain_ChopDownIfLong_WrapFirstCall(): Unit = {
    getCommonSettings.METHOD_CALL_CHAIN_WRAP = CHOP_DOWN_IF_LONG
    getCommonSettings.WRAP_FIRST_METHOD_IN_CALL_CHAIN = true
    setupRightMargin(
      """                               ! """)
    val before =
      """myObject.foo(1, 2).bar(3, 4).baz(5, 6).foo(7, 8).bar(9, 10).baz(11, 12)
        |""".stripMargin
    val after =
      """myObject
        |  .foo(1, 2)
        |  .bar(3, 4)
        |  .baz(5, 6)
        |  .foo(7, 8)
        |  .bar(9, 10)
        |  .baz(11, 12)
        |""".stripMargin
    doTextTest(before, after)
  }

  def test_MethodCallChain_WrapAlways(): Unit = {
    getCommonSettings.METHOD_CALL_CHAIN_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS
    setupRightMargin(
      """                                       ! """)
    val before =
      """myObject.foo(1, 2).foo(1, 2).foo(1, 2)
        |""".stripMargin
    val after =
      """myObject.foo(1, 2)
        |  .foo(1, 2)
        |  .foo(1, 2)
        |""".stripMargin
    doTextTest(before, after)
  }

  def test_MethodCallChain_WrapAlways_WrapFirstCall(): Unit = {
    getCommonSettings.METHOD_CALL_CHAIN_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS
    getCommonSettings.WRAP_FIRST_METHOD_IN_CALL_CHAIN = true
    setupRightMargin(
      """                                       ! """)
    val before =
      """myObject.foo(1, 2).foo(1, 2).foo(1, 2)
        |""".stripMargin
    val after =
      """myObject
        |  .foo(1, 2)
        |  .foo(1, 2)
        |  .foo(1, 2)
        |""".stripMargin
    doTextTest(before, after)
  }

  //
  // Align when multiline
  //

  def test_MethodCallChain_Align_DoNotWrap(): Unit = {
    getCommonSettings.ALIGN_MULTILINE_CHAINED_METHODS = true
    getCommonSettings.METHOD_CALL_CHAIN_WRAP = CommonCodeStyleSettings.DO_NOT_WRAP
    setupRightMargin(
      """                  ! """)
    val before =
      """myObject.foo(1, 2).bar(3, 4)
        |.baz(5, 6).foo(7, 8)
        |.bar(9, 10).baz(11, 12)
        |""".stripMargin
    val after =
      """myObject.foo(1, 2).bar(3, 4)
        |        .baz(5, 6).foo(7, 8)
        |        .bar(9, 10).baz(11, 12)
        |""".stripMargin
    doTextTest(before, after)
  }

  def test_MethodCallChain_Align_DoNotWrap_FirstCallOnNewLine(): Unit = {
    getCommonSettings.ALIGN_MULTILINE_CHAINED_METHODS = true
    getCommonSettings.METHOD_CALL_CHAIN_WRAP = CommonCodeStyleSettings.DO_NOT_WRAP
    setupRightMargin(
      """                  ! """)
    val before =
      """myObject
        |.foo(1, 2).bar(3, 4)
        |.baz(5, 6).foo(7, 8)
        |.bar(9, 10).baz(11, 12)
        |""".stripMargin
    val after =
      """myObject
        |  .foo(1, 2).bar(3, 4)
        |  .baz(5, 6).foo(7, 8)
        |  .bar(9, 10).baz(11, 12)
        |""".stripMargin
    doTextTest(before, after)
  }

  def test_MethodCallChain_Align_DoNotWrap_FirstCallOnNewLine_WithComment(): Unit = {
    getCommonSettings.ALIGN_MULTILINE_CHAINED_METHODS = true
    getCommonSettings.METHOD_CALL_CHAIN_WRAP = CommonCodeStyleSettings.DO_NOT_WRAP
    setupRightMargin(
      """                  ! """)
    val before =
      """myObject // comment
        |.foo(1, 2)
        |.baz(5, 6)
        |
        |foo() // comment
        |.foo(1, 2)
        |.baz(3, 4)
        |
        |foo[String]() // comment
        |.foo(1, 2)
        |.baz(3, 4)
        |""".stripMargin
    val after =
      """myObject // comment
        |  .foo(1, 2)
        |  .baz(5, 6)
        |
        |foo() // comment
        |  .foo(1, 2)
        |  .baz(3, 4)
        |
        |foo[String]() // comment
        |  .foo(1, 2)
        |  .baz(3, 4)
        |""".stripMargin
    doTextTest(before, after)
  }

  def test_MethodCallChain_Align_DoNotWrap_CommentsBetweenCalls(): Unit = {
    getCommonSettings.ALIGN_MULTILINE_CHAINED_METHODS = true
    getCommonSettings.METHOD_CALL_CHAIN_WRAP = CommonCodeStyleSettings.DO_NOT_WRAP
    setupRightMargin(
      """                  ! """)
    val before =
      """myObject.foo().bar()
        | //comment1
        |.foo().bar()
        | /*comment2*/
        |.foo().bar()
        | //comment4
        | /*comment3*/
        | /** comment4 */
        |.foo().bar()
        |
        |myObject/*comment0*/.foo().bar()
        | //comment1
        |.foo().bar()
        |
        |foo()/*comment0*/.foo().bar()
        | //comment1
        |.foo().bar()
        |
        |foo[Int]() /*comment0*/ .foo().bar()
        | //comment1
        |.foo().bar()
        |
        |myObject
        |/*comment0*/
        |.foo().bar()
        | //comment1
        |.foo().bar()
        |""".stripMargin
    val after =
      """myObject.foo().bar()
        |        //comment1
        |        .foo().bar()
        |        /*comment2*/
        |        .foo().bar()
        |        //comment4
        |        /*comment3*/
        |        /** comment4 */
        |        .foo().bar()
        |
        |myObject /*comment0*/ .foo().bar()
        |                      //comment1
        |                      .foo().bar()
        |
        |foo() /*comment0*/ .foo().bar()
        |                   //comment1
        |                   .foo().bar()
        |
        |foo[Int]() /*comment0*/ .foo().bar()
        |                        //comment1
        |                        .foo().bar()
        |
        |myObject
        |  /*comment0*/
        |  .foo().bar()
        |  //comment1
        |  .foo().bar()
        |""".stripMargin
    doTextTest(before, after)
  }

  def test_MethodCallChain_Align_DoNotWrap_CommentsAfterDot(): Unit = {
    getCommonSettings.ALIGN_MULTILINE_CHAINED_METHODS = true
    getCommonSettings.METHOD_CALL_CHAIN_WRAP = CommonCodeStyleSettings.DO_NOT_WRAP
    setupRightMargin(
      """                  ! """)
    val before =
      """myObject.foo().//comment1
        |bar()
        |./*comment2*/foo().bar()
        |""".stripMargin
    val after =
      """myObject.foo(). //comment1
        |        bar()
        |        . /*comment2*/ foo().bar()
        |""".stripMargin
    doTextTest(before, after)
  }

  def test_MethodCallChain_Align_DoNotWrap_CommentsAfterMethodName(): Unit = {
    getCommonSettings.ALIGN_MULTILINE_CHAINED_METHODS = true
    getCommonSettings.METHOD_CALL_CHAIN_WRAP = CommonCodeStyleSettings.DO_NOT_WRAP
    setupRightMargin(
      """                  ! """)
    val before =
      """myObject.foo().bar()
        |.foo/*comment2*/().bar()
        |""".stripMargin
    val after =
      """myObject.foo().bar()
        |        .foo /*comment2*/ ().bar()
        |""".stripMargin
    doTextTest(before, after)
  }

  def test_MethodCallChain_Align_DoNotWrap_CommentsMixed(): Unit = {
    getCommonSettings.ALIGN_MULTILINE_CHAINED_METHODS = true
    getCommonSettings.METHOD_CALL_CHAIN_WRAP = CommonCodeStyleSettings.DO_NOT_WRAP
    setupRightMargin(
      """                  ! """)
    val before =
      """myObject/*comment1*/./*comment2*/foo/*comment3*/()""".stripMargin
    val after =
      """myObject /*comment1*/ . /*comment2*/ foo /*comment3*/ ()""".stripMargin
    doTextTest(before, after)
  }

  def test_MethodCallChain_Align_DoNotWrap_MethodWithoutBrackets(): Unit = {
    getCommonSettings.ALIGN_MULTILINE_CHAINED_METHODS = true
    getCommonSettings.METHOD_CALL_CHAIN_WRAP = CommonCodeStyleSettings.DO_NOT_WRAP
    setupRightMargin(
      """                  ! """)
    val before =
      """myObject.foo.bar
        |.foo.bar()
        |.foo().bar
        |.foo.bar
        |""".stripMargin
    val after =
      """myObject.foo.bar
        |        .foo.bar()
        |        .foo().bar
        |        .foo.bar""".stripMargin
    doTextTest(before, after)
  }

  def test_MethodCallChain_Align_DoNotWrap_WithTypeArguments(): Unit = {
    getCommonSettings.ALIGN_MULTILINE_CHAINED_METHODS = true
    getCommonSettings.METHOD_CALL_CHAIN_WRAP = CommonCodeStyleSettings.DO_NOT_WRAP
    setupRightMargin(
      """                  ! """)
    val before =
      """val myObject = myObject2.foo[String](0)
        |.bar[String](
        | 2
        |)
        |.baz
        |[String](
        |3
        |)
        |.kek
        |[String](4)
      """.stripMargin
    val after =
      """val myObject = myObject2.foo[String](0)
        |                        .bar[String](
        |                          2
        |                        )
        |                        .baz
        |                          [String](
        |                            3
        |                          )
        |                        .kek
        |                          [String](4)""".stripMargin
    doTextTest(before, after)
  }

  @Ignore("waiting for https://youtrack.jetbrains.com/issue/SCL-15163")
  def ignore_MethodCallChain_Align_DoNotWrap_FirstNewLineCallIsMultiline(): Unit = {
    getCommonSettings.ALIGN_MULTILINE_CHAINED_METHODS = true
    getCommonSettings.METHOD_CALL_CHAIN_WRAP = CommonCodeStyleSettings.DO_NOT_WRAP
    setupRightMargin(
      """                  ! """)
    val before =
      """val myObject = myObject2.foo(
        | 1
        |)
        |.bar(
        | 2
        |)
      """.stripMargin
    val after =
      """val myObject = myObject2.foo(
        |                          1
        |                        )
        |                        .bar(
        |                          2
        |                        )
      """.stripMargin
    doTextTest(before, after)
  }

  def test_MethodCallChain_Align_DoNotWrap_FirstNewLineCallIsMultilineAndLast(): Unit = {
    getCommonSettings.ALIGN_MULTILINE_CHAINED_METHODS = true
    getCommonSettings.METHOD_CALL_CHAIN_WRAP = CommonCodeStyleSettings.DO_NOT_WRAP
    setupRightMargin(
      """                  ! """)
    val before =
      """val myObject = myObject2.foo(
        | 1
        |)
      """.stripMargin
    val after =
      """val myObject = myObject2.foo(
        |  1
        |)
      """.stripMargin
    doTextTest(before, after)
  }

  def test_MethodCallChain_Align_WrapIfLong(): Unit = {
    getCommonSettings.ALIGN_MULTILINE_CHAINED_METHODS = true
    getCommonSettings.METHOD_CALL_CHAIN_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED
    setupRightMargin(
      """                              ! """)
    val before =
      """myObject.foo(1, 2).bar(3, 4).baz(5, 6).foo(7, 8).bar(9, 10).looooooongMethod(11, 12)
        |""".stripMargin
    val after =
      """myObject.foo(1, 2).bar(3, 4)
        |        .baz(5, 6).foo(7, 8)
        |        .bar(9, 10)
        |        .looooooongMethod(11, 12)
        |""".stripMargin
    doTextTest(before, after)
  }

  def test_MethodCallChain_Align_ChopDownIfLong(): Unit = {
    getCommonSettings.ALIGN_MULTILINE_CHAINED_METHODS = true
    getCommonSettings.METHOD_CALL_CHAIN_WRAP = CHOP_DOWN_IF_LONG
    setupRightMargin(
      """                                ! """)
    val before =
      """myObject.foo(1, 2).bar(3, 4).baz(5, 6).foo(7, 8).bar(9, 10).baz(11, 12)
        |""".stripMargin
    val after =
      """myObject.foo(1, 2)
        |        .bar(3, 4)
        |        .baz(5, 6)
        |        .foo(7, 8)
        |        .bar(9, 10)
        |        .baz(11, 12)
        |""".stripMargin
    doTextTest(before, after)
  }

  def test_MethodCallChain_Align_ChopDownIfLong_WrapFirstCall(): Unit = {
    getCommonSettings.ALIGN_MULTILINE_CHAINED_METHODS = true
    getCommonSettings.METHOD_CALL_CHAIN_WRAP = CHOP_DOWN_IF_LONG
    getCommonSettings.WRAP_FIRST_METHOD_IN_CALL_CHAIN = true
    setupRightMargin(
      """                                ! """)
    val before =
      """myObject.foo(1, 2).bar(3, 4).baz(5, 6).foo(7, 8).bar(9, 10).baz(11, 12)
        |""".stripMargin
    val after =
      """myObject
        |  .foo(1, 2)
        |  .bar(3, 4)
        |  .baz(5, 6)
        |  .foo(7, 8)
        |  .bar(9, 10)
        |  .baz(11, 12)
        |""".stripMargin
    doTextTest(before, after)
  }

  def test_MethodCallChain_Align_WrapAlways(): Unit = {
    getCommonSettings.ALIGN_MULTILINE_CHAINED_METHODS = true
    getCommonSettings.METHOD_CALL_CHAIN_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS
    setupRightMargin(
      """                                       ! """)
    val before =
      """myObject.foo(1, 2).foo(1, 2).foo(1, 2)
        |""".stripMargin
    val after =
      """myObject.foo(1, 2)
        |        .foo(1, 2)
        |        .foo(1, 2)
        |""".stripMargin
    doTextTest(before, after)
  }

  def test_MethodCallChain_Align_WrapAlways_WrapFirstCall(): Unit = {
    getCommonSettings.ALIGN_MULTILINE_CHAINED_METHODS = true
    getCommonSettings.METHOD_CALL_CHAIN_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS
    getCommonSettings.WRAP_FIRST_METHOD_IN_CALL_CHAIN = true
    setupRightMargin(
      """                                       ! """)
    val before =
      """myObject.foo(1, 2).foo(1, 2).foo(1, 2)
        |""".stripMargin
    val after =
      """myObject
        |  .foo(1, 2)
        |  .foo(1, 2)
        |  .foo(1, 2)
        |""".stripMargin
    doTextTest(before, after)
  }

  def test_MethodCallChain_GenericMethodCall(): Unit = {
    getCommonSettings.ALIGN_MULTILINE_CHAINED_METHODS = false
    getCommonSettings.METHOD_CALL_CHAIN_WRAP = CommonCodeStyleSettings.DO_NOT_WRAP
    setupRightMargin(
      """                  ! """)
    val before =
      """foo[T1, T2](1, 2).bar(3, 4)
        |.baz(5, 6).foo(7, 8)
        |.bar(9, 10).baz(11, 12)
        |""".stripMargin
    val after =
      """foo[T1, T2](1, 2).bar(3, 4)
        |  .baz(5, 6).foo(7, 8)
        |  .bar(9, 10).baz(11, 12)
        |""".stripMargin
    doTextTest(before, after)
  }

  def test_MethodCallChain_GenericMethodCall_Align(): Unit = {
    getCommonSettings.ALIGN_MULTILINE_CHAINED_METHODS = true
    getCommonSettings.METHOD_CALL_CHAIN_WRAP = CommonCodeStyleSettings.DO_NOT_WRAP
    setupRightMargin(
      """                  ! """)
    val before =
      """foo[T1, T2](1, 2).bar(3, 4)
        |.baz(5, 6).foo(7, 8)
        |.bar(9, 10).baz(11, 12)
        |""".stripMargin
    val after =
      """foo[T1, T2](1, 2).bar(3, 4)
        |                 .baz(5, 6).foo(7, 8)
        |                 .bar(9, 10).baz(11, 12)
        |""".stripMargin
    doTextTest(before, after)
  }

  def test_MethodCallChain_GenericMethodCallInTheMiddle_Align_1(): Unit = {
    getCommonSettings.ALIGN_MULTILINE_CHAINED_METHODS = true
    getCommonSettings.METHOD_CALL_CHAIN_WRAP = CommonCodeStyleSettings.DO_NOT_WRAP
    setupRightMargin(
      """                  ! """)
    val before =
      """myObject.method1[String]().method2()
        |.method3()""".stripMargin
    val after =
      """myObject.method1[String]().method2()
        |        .method3()
        |""".stripMargin
    doTextTest(before, after)
  }

  def test_MethodCallChain_GenericMethodCallInTheMiddle_Align_2(): Unit = {
    getCommonSettings.ALIGN_MULTILINE_CHAINED_METHODS = true
    getCommonSettings.METHOD_CALL_CHAIN_WRAP = CommonCodeStyleSettings.DO_NOT_WRAP
    setupRightMargin(
      """                  ! """)
    val before =
      """myObject.method1().method2[String]()
        |.method3()""".stripMargin
    val after =
      """myObject.method1().method2[String]()
        |        .method3()
        |""".stripMargin
    doTextTest(before, after)
  }

  def test_MethodCallChain_GenericMethodCallInTheMiddle_Align_3(): Unit = {
    getCommonSettings.ALIGN_MULTILINE_CHAINED_METHODS = true
    getCommonSettings.METHOD_CALL_CHAIN_WRAP = CommonCodeStyleSettings.DO_NOT_WRAP
    setupRightMargin(
      """                  ! """)
    val before =
      """myObject.method[String]().method()
        |.method.method[T]()
        |.method[String]().method().method()
        |.method.method[T]()
        |.method""".stripMargin
    val after =
      """myObject.method[String]().method()
        |        .method.method[T]()
        |        .method[String]().method().method()
        |        .method.method[T]()
        |        .method
        |""".stripMargin
    doTextTest(before, after)
  }

  // this is some legacy peculiar case when first dot in method call chain starts in on previous line
  // still this does not work properly with wrapping styles different from NO_WRAP
  def test_MethodCallChain_Align_FirstDotOnPrevLine(): Unit = {
    getCommonSettings.ALIGN_MULTILINE_CHAINED_METHODS = true
    val before =
      """val x = foo.
        |foo.goo.
        |foo(1, 2, 3).
        |foo.
        |foo
        |.foo
        |""".stripMargin
    val after =
      """val x = foo.
        |        foo.goo.
        |        foo(1, 2, 3).
        |        foo.
        |        foo
        |        .foo
        |""".stripMargin
    doTextTest(before, after)
  }

  def testIndentForEnumeratorsIfFirstStartsFromNewLine(): Unit = {
    getCommonSettings.ALIGN_MULTILINE_FOR = false
    val before =
      """for (x <- Option(1);
        |y <- Option(2)) yield x * y
        |""".stripMargin
    val after =
      """for (x <- Option(1);
        |  y <- Option(2)) yield x * y
        |""".stripMargin
    doTextTest(before, after)
  }

  def testAlignMultilineFor_1(): Unit = {
    val before =
      """for (x <- Option(1);
        |y <- Option(2)) yield x * y
        |""".stripMargin
    val after =
      """for (x <- Option(1);
        |     y <- Option(2)) yield x * y
        |""".stripMargin
    doTextTest(before, after)
  }

  def testAlignMultilineFor_2(): Unit = {
    val before =
      """for (x <- Option(1);
        |y <- Option(2)
        |) yield x * y
        |""".stripMargin
    val after =
      """for (x <- Option(1);
        |     y <- Option(2)
        |     ) yield x * y
        |""".stripMargin
    doTextTest(before, after)
  }

  def testAlignMultilineFor_3(): Unit = {
    val before =
      """for {x <- Option(1);
        |y <- Option(2)} yield x * y
        |""".stripMargin
    val after =
      """for {x <- Option(1);
        |     y <- Option(2)} yield x * y
        |""".stripMargin
    doTextTest(before, after)
  }

  def testAlignMultilineFor_4(): Unit = {
    val before =
      """for {x <- Option(1);
        |y <- Option(2)
        |} yield x * y
        |""".stripMargin
    val after =
      """for {x <- Option(1);
        |     y <- Option(2)
        |     } yield x * y
        |""".stripMargin
    doTextTest(before, after)
  }

  def testAlignMultilineFor_5(): Unit = {
    val before =
      """for {x <- Option(1);
        |y <- Option(2)} yield {
        |x * y
        |}
        |""".stripMargin
    val after =
      """for {x <- Option(1);
        |     y <- Option(2)} yield {
        |  x * y
        |}
        |""".stripMargin
    doTextTest(before, after)
  }

  def testGroupFieldDeclarationsInColumns(): Unit = {
    getCommonSettings.ALIGN_GROUP_FIELD_DECLARATIONS = true
    val before =
      """var x = 1
        |val yyy = 2
        |val zzzzzz = 3
        |
        |val zzzzzzzz = 4
        |var yyyyy = 5
        |val xxx = 6
        |
        |val qq = 7
        |val wwwwww =
        |  8
        |var eeeeeeeeee = {
        |  9
        |}
        |""".stripMargin
    val after =
      """var x      = 1
        |val yyy    = 2
        |val zzzzzz = 3
        |
        |val zzzzzzzz = 4
        |var yyyyy    = 5
        |val xxx      = 6
        |
        |val qq         = 7
        |val wwwwww     =
        |  8
        |var eeeeeeeeee = {
        |  9
        |}
        |""".stripMargin

    doTextTest(before, after)
  }

  def testGroupCaseArrowsInColumns(): Unit = {
    getScalaSettings.ALIGN_IN_COLUMNS_CASE_BRANCH = true
    val before =
      """someObj1 match {
        |  case x => 1
        |  case yyy => 2
        |  case zzzzzzzzzzz => 3
        |}
        |
        |someObj2 match {
        |  case zzzzzzzzzzzzzz => 4
        |  case yyyyyy => 5
        |  case xxxx => 6
        |}
        |
        |someObj3 match {
        |  case qq =>
        |    4
        |  case wwww =>
        |    5
        |  case eeeeee => 6
        |}
      """.stripMargin
    val after =
      """someObj1 match {
        |  case x           => 1
        |  case yyy         => 2
        |  case zzzzzzzzzzz => 3
        |}
        |
        |someObj2 match {
        |  case zzzzzzzzzzzzzz => 4
        |  case yyyyyy         => 5
        |  case xxxx           => 6
        |}
        |
        |someObj3 match {
        |  case qq     =>
        |    4
        |  case wwww   =>
        |    5
        |  case eeeeee => 6
        |}
        |""".stripMargin

    doTextTest(before, after)
  }

  def testClosureBraceForce_1(): Unit = {
    val before =
      """
        |Seq(1, 2).map(x =>
        |  42
        |)
      """.stripMargin
    val after =
      """
        |Seq(1, 2).map(x => {
        |  42
        |})
      """.stripMargin

    getScalaSettings.CLOSURE_BRACE_FORCE = CommonCodeStyleSettings.FORCE_BRACES_ALWAYS

    getScalaSettings.PLACE_CLOSURE_PARAMETERS_ON_NEW_LINE = false
    doTextTest(before, after)

    getScalaSettings.PLACE_CLOSURE_PARAMETERS_ON_NEW_LINE = true
    doTextTest(before, after)
  }

  def tesClosureBraceForce_2(): Unit = {
    val before =
      """
        |Seq(1, 2).map(x => {
        |  42
        |})
      """.stripMargin

    getScalaSettings.CLOSURE_BRACE_FORCE = CommonCodeStyleSettings.FORCE_BRACES_ALWAYS

    getScalaSettings.PLACE_CLOSURE_PARAMETERS_ON_NEW_LINE = false
    doTextTest(before)

    getScalaSettings.PLACE_CLOSURE_PARAMETERS_ON_NEW_LINE = true
    doTextTest(before)
  }

  def testClosureBraceForce_3_ShouldNotAffectLambdaWrappedWithBraces(): Unit = {
    val before =
      """Seq(1, 2).map { x =>
        |  42
        |}
      """.stripMargin

    getScalaSettings.CLOSURE_BRACE_FORCE = CommonCodeStyleSettings.FORCE_BRACES_IF_MULTILINE
    doTextTest(before)

    getScalaSettings.CLOSURE_BRACE_FORCE = CommonCodeStyleSettings.FORCE_BRACES_ALWAYS
    doTextTest(before)
  }

  def testMethodDeclarationParametersWrapShouldNotEffectLambdaArgument(): Unit = {
    val before = """Seq(1, 2).filter { a => a % 2 == 0 }"""

    getCommonSettings.METHOD_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED
    doTextTest(before)

    getCommonSettings.METHOD_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS
    doTextTest(before)

    getCommonSettings.METHOD_PARAMETERS_WRAP = CHOP_DOWN_IF_LONG
    doTextTest(before)
  }

  def testDoNotIndentCaseClauseBody_1(): Unit = {
    getScalaSettings.DO_NOT_INDENT_CASE_CLAUSE_BODY = true
    val before =
      """Option(1) match {
        |  case Some(1) =>
        |    42
        |  case _ =>
        |    23
        |}
      """.stripMargin
    val after =
      """Option(1) match {
        |  case Some(1) =>
        |  42
        |  case _ =>
        |  23
        |}
      """.stripMargin
    doTextTest(before, after)
  }

  def testDoNotIndentCaseClauseBody_2(): Unit = {
    getScalaSettings.DO_NOT_INDENT_CASE_CLAUSE_BODY = true
    val before =
      """Seq(1, 2).map {
        |  case 1 =>
        |    "1"
        |  case 2 =>
        |    "2"
        |  case _ =>
        |    "other"
        |}
      """.stripMargin
    val after =
      """Seq(1, 2).map {
        |  case 1 =>
        |  "1"
        |  case 2 =>
        |  "2"
        |  case _ =>
        |  "other"
        |}
      """.stripMargin
    doTextTest(before, after)
  }

  def testDoNotIndentCaseClauseBodySettingShouldNotEffectLambdaBody(): Unit = {
    getScalaSettings.DO_NOT_INDENT_CASE_CLAUSE_BODY = true
    val before =
      """Seq(1, 2).map { x =>
        |  42
        |}
        |
        |Seq(1, 2).map { case x =>
        |  42
        |}
      """.stripMargin
    doTextTest(before)
  }

  def testIndentTypeParametersAndArguments(): Unit = {
    getScalaSettings.INDENT_TYPE_PARAMETERS = true
    getScalaSettings.INDENT_TYPE_ARGUMENTS = true
    getIndentOptions.CONTINUATION_INDENT_SIZE = 3

    val before =
      """def foo[
        |A, B,
        |C <: T[
        |D, E,
        |F
        |]
        |]: Unit
      """.stripMargin
    val after =
      """def foo[
        |   A, B,
        |   C <: T[
        |      D, E,
        |      F
        |   ]
        |]: Unit
      """.stripMargin
    doTextTest(before, after)
  }

  def testIndentTypeParametersWithoutArguments(): Unit = {
    getScalaSettings.INDENT_TYPE_PARAMETERS = true
    getScalaSettings.INDENT_TYPE_ARGUMENTS = false
    getIndentOptions.CONTINUATION_INDENT_SIZE = 3

    val before =
      """def foo[
        |A, B,
        |C <: T[
        |D, E,
        |F
        |]
        |]: Unit
      """.stripMargin
    val after =
      """def foo[
        |   A, B,
        |   C <: T[
        |   D, E,
        |   F
        |   ]
        |]: Unit
      """.stripMargin
    doTextTest(before, after)
  }

  def testIndentTypeArgumentsWithoutParameters(): Unit = {
    getScalaSettings.INDENT_TYPE_PARAMETERS = false
    getScalaSettings.INDENT_TYPE_ARGUMENTS = true
    getIndentOptions.CONTINUATION_INDENT_SIZE = 3

    val before =
      """def foo[
        |A, B,
        |C <: T[
        |D, E,
        |F
        |]
        |]: Unit
      """.stripMargin
    val after =
      """def foo[
        |A, B,
        |C <: T[
        |   D, E,
        |   F
        |]
        |]: Unit
      """.stripMargin
    doTextTest(before, after)
  }

  def testIfElseWithoutBraces(): Unit = doTextTest(
    """if (true)
      |  1
      |else
      |  2
      |""".stripMargin
  )

  def testIfElseWithoutBraces_1(): Unit = doTextTest(
    """if (true)
      |  1
      |else if (true)
      |  2
      |else
      |  3
      |""".stripMargin
  )

  def testIfElseWithoutBraces_WithComment(): Unit = doTextTest(
    """if (true)
      |  1 // comment
      |else
      |  2
      |""".stripMargin
  )

  def testIfElseWithoutBraces_WithComment_1(): Unit = doTextTest(
    """if (true)
      |  1
      |else
      |  2 // comment
      |""".stripMargin
  )

  def testIfElseWithoutBraces_WithComment_2(): Unit = doTextTest(
    """if (true)
      |  1 // comment 1
      |else if (true)
      |  2 // comment 2
      |else
      |  3
      |""".stripMargin
  )

  def testIfElseWithoutBraces_WithComment_3(): Unit = doTextTest(
    """if (true)
      |  1 /* block
      |comment */
      |else
      |  2 // comment 2
      |""".stripMargin
  )

  def testIfElseWithoutBraces_WithComment_4(): Unit = doTextTest(
    """if (false) // comment
      |  1
      |else if (false) // comment
      |  2
      |else // comment
      |  3
      |""".stripMargin
  )

  def testIfElseWithoutBraces_WithComment_5(): Unit = doTextTest(
    """// comment
      |if (false)
      |  1
      |// comment
      |else if (false)
      |  2
      |// comment
      |else
      |  3
      |""".stripMargin
  )

  def testIfElseWithoutBraces_WithComment_6(): Unit = doTextTest(
    """// comment
      |if (false) // comment
      |  1
      |// comment
      |else if (false) // comment
      |  2
      |// comment
      |else // comment
      |  3
      |""".stripMargin
  )

  def testIfElseWithoutBraces_WithComment_7(): Unit = doTextTest(
    """if (false) // comment
      |  1 // comment
      |else if (false) // comment
      |  2 // comment
      |else // comment
      |  3 // comment
      |""".stripMargin
  )

  def testConstructorBody_WithBraces(): Unit = doTextTest(
    """class A {
      |  def this(x: Long) = {
      |    this()
      |  }
      |
      |  def this(x: Short) = {
      |    this()
      |    println(1)
      |  }
      |
      |  def this(x: Long, y: Long) = {
      |    this()
      |  }
      |
      |  def this(x: Short, y: Short) = {
      |    this()
      |    println(1)
      |  }
      |}
      |""".stripMargin
  )

  def testConstructorBody_WithoutBraces(): Unit = doTextTest(
    """class A {
      |  def this(x: Int) =
      |    this()
      |
      |  def this(x: String) =
      |    this()
      |}
      |""".stripMargin
  )
}