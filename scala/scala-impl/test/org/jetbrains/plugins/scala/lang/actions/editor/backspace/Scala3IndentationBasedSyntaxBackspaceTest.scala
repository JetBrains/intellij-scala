package org.jetbrains.plugins.scala.lang.actions.editor.backspace

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.editorActions.SmartBackspaceMode
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.lang.actions.editor.enter.scala3.Scala3TestDataBracelessCode.WrapperCodeContexts
import org.jetbrains.plugins.scala.settings.ScalaCompileServerSettings

class Scala3IndentationBasedSyntaxBackspaceTest extends ScalaBackspaceHandlerBaseTest {

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= ScalaVersion.Latest.Scala_3_0

  // copied from Scala3BracelessSyntaxEnterExhaustiveTest
  override def setUp(): Unit = {
    super.setUp()
    ScalaCompileServerSettings.getInstance.COMPILE_SERVER_ENABLED = false
    getScalaCodeStyleSettings.USE_SCALA3_INDENTATION_BASED_SYNTAX = true
  }

  def testMethodBody_Simple_TailPosition(): Unit =
    doSequentialBackspaceTest_InAllWrapperContexts(
      """def f1 =
        |  111#
        |  #    #
        |""".stripMargin
    )

  def testMethodBody_MultipleExpressions_TailPosition(): Unit =
    doSequentialBackspaceTest_InAllWrapperContexts(
      """def f1 =
        |  111
        |  222
        |  333#
        |  #    #
        |""".stripMargin
    )

  def testMethodBody_MultipleExpressions_BlankLinesWithSpacesBeforeCaret(): Unit =
    doSequentialBackspaceTest_InContexts(WrapperContextsWithJumpToPreviousLine)(
      s"""def f1 =
         |  111
         |  222
         |  333#
         |#    ${""}
         |#    ${""}
         |#    ${""}
         |# #    #
         |""".stripMargin
    )

  def testNestedMethodBody_1_Simple_TailPosition(): Unit =
    doSequentialBackspaceTest_InAllWrapperContexts(
      """def f1 =
        |  def f2 =
        |    def f3 =
        |      "f3"#
        |      #    #
        |    "f2" + f3
        |  "f1" + f2""".stripMargin
    )

  def testNestedMethodBody_1_MultipleExpressions_TailPosition(): Unit =
    doSequentialBackspaceTest_InAllWrapperContexts(
      """def f1 =
        |  def f2 =
        |    def f3 =
        |      111
        |      222
        |      "f3"#
        |      #    #
        |    "f2" + f3
        |  "f1" + f2""".stripMargin
    )

  def testNestedMethodBody_2_Simple_TailPosition(): Unit =
    doSequentialBackspaceTest_InAllWrapperContexts(
      """def f1 =
        |  def f2 =
        |    def f3 =
        |      "f3"#
        |      #    #
        |    "f2" + f3
        |  "f1" + f2""".stripMargin
    )

  def testNestedMethodBody_2_MultipleExpressions_TailPosition(): Unit =
    doSequentialBackspaceTest_InAllWrapperContexts(
      """def f1 =
        |  def f2 =
        |    def f3 =
        |      111
        |      222
        |      "f3"#
        |      #    #
        |""".stripMargin
    )

  def testNestedMethod_BlankLineBeforeCaret_1_F1WithEndMarker_ClassWithEndMarker(): Unit =
    doSequentialBackspaceTest(
      s"""class A:
         |  def f1 =
         |    def f2 =
         |      def f3 =
         |        "f3"#
         |    #
         |    # ##
         |  end f1
         |end A""".stripMargin
    )

  def testNestedMethod_BlankLineBeforeCaret_2_F2WithoutEndMarker_ClassWithEndMarker(): Unit =
    doSequentialBackspaceTest(
      s"""class A:
         |  def f1 =
         |    def f2 =
         |      def f3 =
         |        "f3"#
         |  #
         |  # # ##
         |end A""".stripMargin
    )

  def testNestedMethod_BlankLineBeforeCaret_3_F2WithoutEndMarker_ClassWithoutEndMarker(): Unit =
    doSequentialBackspaceTest(
      s"""class A:
         |  def f1 =
         |    def f2 =
         |      def f3 =
         |        "f3"#
         |#
         |# # # ##
         |""".stripMargin
    )

  def testShouldRespectBackspaceModeSetting(): Unit = {
    val settings = CodeInsightSettings.getInstance
    val backspaceModeBefore = settings.getBackspaceMode

    try {
      settings.setBackspaceMode(SmartBackspaceMode.INDENT)
      doSequentialBackspaceTest(
        """def f1 =
          |  def f2 =
          |    def f3 =
          |      "f3"
          |# # # # # ##
          |    "f2" + f3
          |  "f1" + f2""".stripMargin
      )
    } finally {
      settings.setBackspaceMode(backspaceModeBefore)
    }
  }

  def testExample1_NestedMethod_TailPosition(): Unit =
    doSequentialBackspaceTest(
      """class A {
        |  def foo =
        |    println(1)
        |    println(2)
        |
        |  def fooOuter =
        |    println(1)
        |    println(2)
        |
        |    def fooInner =
        |      println(1)
        |      println(2)#
        |      #    #
        |}
        |""".stripMargin
    )

  def testExample1_NestedMethod_TailPosition_CaretUnindented(): Unit =
    doSequentialBackspaceTest(
      """class A {
        |  def foo =
        |    println(1)
        |    println(2)
        |
        |  def fooOuter =
        |    println(1)
        |    println(2)
        |
        |    def fooInner =
        |      println(1)
        |      println(2)#
        |    ##
        |}
        |""".stripMargin
    )

  def testExample1_NestedMethod_BlankLineBeforeCaret(): Unit =
    doSequentialBackspaceTest(
      """class A {
        |  def foo =
        |    println(1)
        |    println(2)
        |
        |  def fooOuter =
        |    println(1)
        |    println(2)
        |
        |    def fooInner =
        |      println(1)
        |      println(2)#
        |  #
        |  # # #    #
        |}
        |""".stripMargin
    )

  def testExample2_NestedMethod_TailPosition(): Unit =
    doSequentialBackspaceTest(
      """class A {
        |
        |  def foo =
        |    println(1)
        |    println(2)
        |
        |  def fooOuter =
        |    println(1)
        |    println(2)#
        |    #   #
        |
        |    def fooInner =
        |      println(1)
        |      println(2)
        |}
        |""".stripMargin
    )

  def testExample2_NestedMethod_BlankLineBeforeCaret(): Unit =
    doSequentialBackspaceTest(
      """class A {
        |
        |  def foo =
        |    println(1)
        |    println(2)#
        |  #
        |  # #   #
        |
        |  def fooOuter =
        |    println(1)
        |    println(2)
        |
        |    def fooInner =
        |      println(1)
        |      println(2)
        |}
        |""".stripMargin
    )

  def testExample2_NestedMethod_TailPosition_CaretUnindented(): Unit =
    doSequentialBackspaceTest(
      """class A {
        |
        |  def foo =
        |    println(1)
        |    println(2)#
        |  ##
        |
        |  def fooOuter =
        |    println(1)
        |    println(2)
        |
        |    def fooInner =
        |      println(1)
        |      println(2)
        |
        |}
        |""".stripMargin
    )

  def testExample3_NestedMethod_TailPosition_WithRedundantSpacesAfterCaretOnBlankLine(): Unit =
    doTest(
      s"""class Example1:
         |  def outer =
         |    println("outer 1")
         |    println("outer 2")
         |
         |    def inner =
         |      println("inner 1")
         |      println("inner 2")
         |      "inner result"
         |    $CARET  ${""}
         |
         |    "outer result" + inner
         |""".stripMargin,
      s"""class Example1:
         |  def outer =
         |    println("outer 1")
         |    println("outer 2")
         |
         |    def inner =
         |      println("inner 1")
         |      println("inner 2")
         |      "inner result"$CARET
         |
         |    "outer result" + inner
         |""".stripMargin
    )

  def testCodeAfterCaret(): Unit =
    doTest(
      s"""class A:
         |  def foo1 =
         |    def foo2 =
         |      111
         |      222
         |             ${CARET}333
         |""".stripMargin,
      s"""class A:
         |  def foo1 =
         |    def foo2 =
         |      111
         |      222
         |      ${CARET}333
         |""".stripMargin
    )

  def testCodeAfterCaret_WithLeadingSpaces(): Unit =
    doTest(
      s"""class A:
         |  def foo1 =
         |    def foo2 =
         |      111
         |      222
         |             $CARET   333
         |""".stripMargin,
      s"""class A:
         |  def foo1 =
         |    def foo2 =
         |      111
         |      222
         |      ${CARET}333
         |""".stripMargin
    )

  def testMethod_InsideBody(): Unit =
    doSequentialBackspaceTest_InAllWrapperContexts(
      """def f1 =
        |  111#
        |  #
        |  #
        |  #   #
        |  222
        |""".stripMargin
    )

  def testNestedMethod_1_InsideBody(): Unit =
    doSequentialBackspaceTest_InAllWrapperContexts(
      """def f1 =
        |  def f2 =
        |    111#
        |    #
        |    #
        |    #    #
        |    222
        |""".stripMargin
    )

  def testNestedMethod_2_InsideBody(): Unit =
    doSequentialBackspaceTest_InAllWrapperContexts(
      """def f1 = {
        |  def f2 =
        |    111
        |    222
        |
        |    def f3 =
        |      333#
        |      #
        |      #        #
        |      444
        |}""".stripMargin
    )

  def testAfterLastCaseClause_TailPosition(): Unit =
    doSequentialBackspaceTest_InAllWrapperContexts(
      s"""Option(42) match
         |  case Some(1) =>
         |    111#
         |    #   #
         |""".stripMargin
    )

  def testAfterLastCaseClause_BlankLinesBeforeCaret_1(): Unit =
    doSequentialBackspaceTest_InContexts(WrapperContextsWithJumpToPreviousLine)(
      s"""class Dummy
         |
         |Option(42) match
         |  case Some(1) =>
         |    111#
         |#
         |# # #   #
         |""".stripMargin
    )

  def testAfterLastCaseClause_BlankLinesBeforeCaret_2(): Unit =
    doSequentialBackspaceTest_InContexts(WrapperContextsWithJumpToPreviousLine)(
      s"""Option(42) match
         |  case Some(1) =>
         |    111#
         |#
         |# # #   #
         |
         |class Dummy
         |""".stripMargin
    )

  def testAfterLastCaseClause_InNestedScope_BlankLinesBeforeCaret(): Unit =
    doSequentialBackspaceTest_InContexts(WrapperContextsWithJumpToPreviousLine)(
      s"""def foo =
         |  val res =
         |    Option(42) match
         |      case Some(1) =>
         |        111#
         |#
         |# # # # #    #
         |""".stripMargin
    )

  def testAfterLastCaseClause_InNestedScope_TailPosition_CaretUnindented_1(): Unit =
    doSequentialBackspaceTest_InAllWrapperContexts(
      s"""def foo =
         |  val res =
         |    Option(42) match
         |      case Some(1) =>
         |        111#
         |    ##
         |""".stripMargin
    )

  def testAfterLastCaseClause_InNestedScope_TailPosition_CaretUnindented_2(): Unit =
    doSequentialBackspaceTest_InAllWrapperContexts(
      s"""def foo =
         |  val res =
         |    Option(42) match
         |      case Some(1) =>
         |        111#
         |  ##
         |""".stripMargin
    )

  def testAfterLastCaseClause_InNestedScope_TailPosition_CaretUnindented_3(): Unit =
    doSequentialBackspaceTest_InAllWrapperContexts(
      s"""def foo =
         |  val res =
         |    Option(42) match
         |      case Some(1) =>
         |        111#
         |##
         |""".stripMargin
    )


  def testAfterMiddleCaseClause_InNestedScope_1(): Unit =
    doSequentialBackspaceTest_InAllWrapperContexts(
      s"""Option(42) match
         |  case Some(1) =>
         |    111#
         |    #
         |    #   #
         |  case _ =>
         |""".stripMargin
    )

  def testAfterMiddleCaseClause_InNestedScope_2_UnindentedCaret(): Unit =
    doSequentialBackspaceTest_InAllWrapperContexts(
      s"""Option(42) match
         |  case Some(1) =>
         |    111#
         |    #
         |  #
         |  case _ =>
         |""".stripMargin
    )

  def testAfterMiddleCaseClause_WithCodeAfterCaret_1(): Unit =
    doTest(
      s"""def foo = {
         |  Option(42) match
         |    case Some(1) =>
         |      ${CARET}111
         |    case _ =>
         |}
         |""".stripMargin,
      s"""def foo = {
         |  Option(42) match
         |    case Some(1) => ${CARET}111
         |    case _ =>
         |}
         |""".stripMargin
    )

  def testAfterMiddleCaseClause_WithCodeAfterCaret_2(): Unit =
    doTest(
      s"""def foo = {
         |  Option(42) match
         |    case Some(1) =>
         | $CARET     111
         |    case _ =>
         |}
         |""".stripMargin,
      s"""def foo = {
         |  Option(42) match
         |    case Some(1) => ${CARET}111
         |    case _ =>
         |}
         |""".stripMargin
    )

  def testAfterMiddleCaseClause_WithCodeAfterCaret_3(): Unit =
    doTest(
      s"""def foo = {
         |  Option(42) match
         |    case Some(1) =>
         |      111
         | $CARET     222
         |    case _ =>
         |}
         |""".stripMargin,
      s"""def foo = {
         |  Option(42) match
         |    case Some(1) =>
         |      111${CARET}222
         |    case _ =>
         |}
         |""".stripMargin
    )

  def testAfterMiddleCaseClause_WithIndentedCodeAfterCaret_1(): Unit =
    doTest(
      s"""def foo = {
         |  Option(42) match
         |    case Some(1) =>
         |        $CARET    42
         |    case _ =>
         |}
         |""".stripMargin,
      s"""def foo = {
         |  Option(42) match
         |    case Some(1) =>
         |      ${CARET}42
         |    case _ =>
         |}
         |""".stripMargin
    )

  def testAfterMiddleCaseClause_WithIndentedCodeAfterCaret_2(): Unit =
    doTest(
      s"""def foo = {
         |  Option(42) match
         |    case Some(1) =>
         |          ${CARET}42
         |    case _ =>
         |}
         |""".stripMargin,
      s"""def foo = {
         |  Option(42) match
         |    case Some(1) =>
         |      ${CARET}42
         |    case _ =>
         |}
         |""".stripMargin
    )


  def testBetweenCaseClauses_WithCodeAfterCaseClauseArrow(): Unit =
    doSequentialBackspaceTest_InAllWrapperContexts(
      s"""42 match
         |  case 1 => 1#
         |  #
         |  #
         |  #   #
         |  case 2 => 2
         |""".stripMargin
    )

  def testAfterCaseClauses_WithCodeAfterCaseClauseArrow(): Unit =
    doSequentialBackspaceTest_InAllWrapperContexts(
      s"""42 match
         |  case 1 => 1
         |  case 2 => 2#
         |  #   #
         |""".stripMargin
    )

  def testAfterCaseClauses_WithCodeAfterCaseClauseArrow_BlankLineBefore(): Unit =
    doSequentialBackspaceTest_InContexts(WrapperContextsWithJumpToPreviousLine)(
      s"""42 match
         |  case 1 => 1
         |  case 2 => 2#
         |#
         |# #   #
         |""".stripMargin
    )

  def testAfterCaseClauses_WithCodeAfterCaseClauseArrow_BlankLineBefore_1(): Unit =
    doSequentialBackspaceTest(
      s"""class A:
         |  class B:
         |    42 match
         |      case 1 => 1
         |      case 2 => 2#
         |#
         |# # # #   #
         |""".stripMargin
    )

  def testAfterIncompleteMatch(): Unit =
    doSequentialBackspaceTest_InContexts(WrapperCodeContexts.AllContexts.filterNot(_ == WrapperCodeContexts.InsideCaseClausesNonLast))(
      s"""Option(42) match#
         |  #    #
         |""".stripMargin
    )

  def testUnnamed1(): Unit =
    doTest(
      s"""def foo1 =
         |  111
         |  222
         |  def foo2 =
         |    333
         |    Seq(
         |      1,
         |      2,
         |      ${CARET}3
         |    )
         |    444
         |""".stripMargin,
      s"""def foo1 =
         |  111
         |  222
         |  def foo2 =
         |    333
         |    Seq(
         |      1,
         |      2, ${CARET}3
         |    )
         |    444
         |""".stripMargin
    )

  def test_backspace_at_end(): Unit =
    performTest(
      s"""class A {
         |}
         |  $CARET${"      "}""".stripMargin,
      s"""class A {
         |}
         |$CARET
         |""".stripMargin.trim
    ){
      () => performBackspaceAction()
    }


  def testBackspaceHandlerShouldWorkEvenWhenCodeStyleSettingIsDisabled(): Unit = {
    val before = getScalaCodeStyleSettings.USE_SCALA3_INDENTATION_BASED_SYNTAX
    getScalaCodeStyleSettings.USE_SCALA3_INDENTATION_BASED_SYNTAX = false

    try {
      doSequentialBackspaceTest(
        """class A {
          |  def foo =
          |    println(1)
          |    println(2)
          |
          |  def fooOuter =
          |    println(1)
          |    println(2)
          |
          |    def fooInner =
          |      println(1)
          |      println(2)#
          |  #
          |  # # #    #
          |}
          |""".stripMargin
      )
    } finally {
      getScalaCodeStyleSettings.USE_SCALA3_INDENTATION_BASED_SYNTAX = before
    }
  }

  def testBackspaceInOpenPackaging(): Unit = doSequentialBackspaceTest(
    """
      |packaging A:
      |  val x = 0#
      |#
      |# # #
      |""".stripMargin
  )

  def testBackspaceInClosedPackaging(): Unit = doSequentialBackspaceTest(
    """
      |package A:
      |  val x = 0#
      |  #
      |  # #
      |end A
      |""".stripMargin
  )

  def testBackspaceInTwoOpenPackagings(): Unit = doSequentialBackspaceTest(
    """
      |package A:
      |  package B:
      |    val x =
      |      0#
      |#
      |# # # # #
      |""".stripMargin
  )

  def testBackspaceNextLineInTwoOpenPackagings(): Unit = doSequentialBackspaceTest(
    """
      |package A:
      |  package B:
      |    val x = 0#
      |    # #
      |""".stripMargin
  )

  def testBackspaceInOpenPackagingInClosedPackaging(): Unit = doSequentialBackspaceTest(
    """
      |package A:
      |  package B:
      |    val x = 0#
      |  #
      |  # # #
      |end A
      |""".stripMargin
  )
}
