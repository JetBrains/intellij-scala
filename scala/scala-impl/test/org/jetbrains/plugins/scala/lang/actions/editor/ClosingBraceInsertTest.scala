package org.jetbrains.plugins.scala.lang.actions.editor

import com.intellij.testFramework.fixtures.CodeInsightTestFixture.{CARET_MARKER => CARET}
import org.jetbrains.plugins.scala.base.EditorActionTestBase

class ClosingBraceInsertTest extends EditorActionTestBase {

  private def doTest(before: String, after: String): Unit = {
    checkGeneratedTextAfterTyping(before, after, '{')
  }

  def testInsertClosingBrace_ForStatement(): Unit = {
    val before = s"for (_ <- Seq()) $CARET"
    val after = s"for (_ <- Seq()) {$CARET}"
    doTest(before, after)
  }
  
  def testInsertClosingBrace_EmptyFunctionBody(): Unit = {
    val before = s"def foo = $CARET"
    val after = s"def foo = {$CARET}"
    doTest(before, after)
  }

  def testInsertClosingBrace_EmptyFunctionBody_UsingTabsInsteadOfSpaces(): Unit = {
    val indentOptions = getCommonSettings.getIndentOptions
    indentOptions.USE_TAB_CHARACTER = true
    indentOptions.TAB_SIZE = 4
    val before =
      s"""class A {
         |  {
         |\t   def foo = $CARET
         |\t\tobj.methodCall
         |  }
         |}""".stripMargin
    val after =
      s"""class A {
         |  {
         |\t   def foo = {
         |\t\t${CARET}obj.methodCall
         |\t   }
         |  }
         |}""".stripMargin

    doTest(before, after)
  }

  def testNotInsertClosingBrace_EmptyFunctionBody_UsingTabsInsteadOfSpaces(): Unit = {
    val indentOptions = getCommonSettings.getIndentOptions
    indentOptions.USE_TAB_CHARACTER = true
    indentOptions.TAB_SIZE = 4
    val before =
      s"""class A {
         |  {
         |\tdef foo = $CARET
         |   obj.methodCall
         |  }
         |}""".stripMargin
    val after =
      s"""class A {
         |  {
         |\tdef foo = {$CARET}
         |   obj.methodCall
         |  }
         |}""".stripMargin

    doTest(before, after)
  }

  def testInsertClosingBrace_FunctionIndentedBody(): Unit = {
    val before =
      s"""def foo = $CARET
         |  obj.method()
         |    .method()
         |someUnrelatedCode()
         |""".stripMargin
    val after =
      s"""def foo = {
         |  ${CARET}obj.method()
         |    .method()
         |}
         |someUnrelatedCode()
         |""".stripMargin
    doTest(before, after)
  }

  def testInsertClosingBrace_FunctionIndentedBody_CaretRightAfterEqualsCharacter(): Unit = {
    val before =
      s"""def foo =$CARET
         |  obj.method()
         |    .method()
         |someUnrelatedCode()
         |""".stripMargin
    val after =
      s"""def foo = {
         |  ${CARET}obj.method()
         |    .method()
         |}
         |someUnrelatedCode()
         |""".stripMargin
    doTest(before, after)
  }

  def testInsertClosingBrace_FunctionIndentedBodyWithOneLineCommentBeforeBody(): Unit = {
    val before =
      s"""def foo = $CARET // comment line
         |  obj.method()
         |    .method
         |
         |someUnrelatedCode()
         |""".stripMargin
    val after =
      s"""def foo = { // comment line
         |  ${CARET}obj.method()
         |    .method
         |}
         |
         |someUnrelatedCode()
         |""".stripMargin
    doTest(before, after)
  }

  def testInsertClosingBrace_FunctionIndentedBodyWithOneLineCommentAfterBody(): Unit = {
    val before =
      s"""def foo = $CARET
         |  obj.method() // comment line
         |
         |someUnrelatedCode()
         |""".stripMargin
    val after =
      s"""def foo = {
         |  ${CARET}obj.method() // comment line
         |}
         |
         |someUnrelatedCode()
         |""".stripMargin
    doTest(before, after)
  }

  def testInsertClosingBrace_FunctionIndentedBodyWithBlockCommentBeforeBody(): Unit = {
    val before =
      s"""def foo = $CARET /* block comment */
         |  obj.method()
         |    .method
         |
         |someUnrelatedCode()
         |""".stripMargin
    val after =
      s"""def foo = { /* block comment */
         |  ${CARET}obj.method()
         |    .method
         |}
         |
         |someUnrelatedCode()
         |""".stripMargin
    doTest(before, after)
  }

  def testNotInsertClosingBrace_FunctionNonIndentedBody(): Unit = {
    val before =
      s"""def foo = $CARET
         |someUnrelatedCode1()
         |someUnrelatedCode2()
         |""".stripMargin
    val after =
      s"""def foo = {$CARET}
         |someUnrelatedCode1()
         |someUnrelatedCode2()
         |""".stripMargin
    doTest(before, after)
  }

  def testNotInsertClosingBrace_FunctionBodyWithCaretAtBodyStart(): Unit = {
    val before =
      s"""def foo = ${CARET}obj.method()
         |  .method()
         |someUnrelatedCode()
         |""".stripMargin
    val after =
      s"""def foo = {${CARET}obj.method()
         |  .method()
         |someUnrelatedCode()
         |""".stripMargin
    doTest(before, after)
  }

  def testNotInsertClosingBrace_FunctionBodyWithCaretAtBodyStart_1(): Unit = {
    val before =
      s"""def foo =
         |  ${CARET}obj.method()
         |    .method()
         |someUnrelatedCode()
         |""".stripMargin
    val after =
      s"""def foo =
         |  {${CARET}obj.method()
         |    .method()
         |someUnrelatedCode()
         |""".stripMargin
    doTest(before, after)
  }

  def testNotInsertClosingBrace_FunctionBodyWithCaretAtBodyStart_2(): Unit = {
    val before =
      s"""def foo = ${CARET}obj.method()
         |
         |someUnrelatedCode()
         |""".stripMargin
    val after =
      s"""def foo = {${CARET}obj.method()
         |
         |someUnrelatedCode()
         |""".stripMargin
    doTest(before, after)
  }

  def testNotInsertClosingBrace_FunctionBodyWithCaretAtBodyStart_3(): Unit = {
    val before =
      s"""def foo = $CARET   obj.method()
         |
         |someUnrelatedCode()
         |""".stripMargin
    val after =
      s"""def foo = {$CARET   obj.method()
         |
         |someUnrelatedCode()
         |""".stripMargin
    doTest(before, after)
  }

  def testInsertClosingBrace_ValIndentedInitializationBody(): Unit = {
    val before =
      s"""val x = $CARET
         |  obj.method()
         |    .method()
         |
         |someUnrelatedCode()
         |""".stripMargin
    val after =
      s"""val x = {
         |  ${CARET}obj.method()
         |    .method()
         |}
         |
         |someUnrelatedCode()
         |""".stripMargin
    doTest(before, after)
  }

  def testInsertClosingBrace_VarIndentedInitializationBody(): Unit = {
    val before =
      s"""var x = $CARET
         |  obj.method()
         |    .method()
         |
         |someUnrelatedCode()
         |""".stripMargin
    val after =
      s"""var x = {
         |  ${CARET}obj.method()
         |    .method()
         |}
         |
         |someUnrelatedCode()
         |""".stripMargin
    doTest(before, after)
  }

  def testInsertClosingBrace_LazyValIndentedInitializationBody(): Unit = {
    val before =
      s"""lazy val x = $CARET
         |  obj.method()
         |    .method()
         |someUnrelatedCode()
         |""".stripMargin
    val after =
      s"""lazy val x = {
         |  ${CARET}obj.method()
         |    .method()
         |}
         |someUnrelatedCode()
         |""".stripMargin
    doTest(before, after)
  }

  def testNotInsertClosingBrace_ValWithMultipleBindings(): Unit = {
    val before =
      s"""val x = $CARET
         |obj.method()
         |  .method()
         |
         |someUnrelatedCode()
         |""".stripMargin
    val after =
      s"""val x = {$CARET}
         |obj.method()
         |  .method()
         |
         |someUnrelatedCode()
         |""".stripMargin
    doTest(before, after)
  }

  def testNotInsertClosingBrace_ValNonIndentedInitializationBody(): Unit = {
    val before =
      s"""val (x, y) = $CARET
         |  (42, 23)
         |
         |someUnrelatedCode()
         |""".stripMargin
    val after =
      s"""val (x, y) = {$CARET}
         |  (42, 23)
         |
         |someUnrelatedCode()
         |""".stripMargin
    doTest(before, after)
  }

}
