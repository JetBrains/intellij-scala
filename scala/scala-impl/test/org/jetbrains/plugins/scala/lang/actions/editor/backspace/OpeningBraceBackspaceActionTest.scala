package org.jetbrains.plugins.scala.lang.actions.editor.backspace

import com.intellij.testFramework.fixtures.CodeInsightTestFixture.{CARET_MARKER => CARET}
import org.jetbrains.plugins.scala.base.EditorActionTestBase

class OpeningBraceBackspaceActionTest extends EditorActionTestBase {

  private def doTest(before: String, after: String): Unit = {
    checkGeneratedTextAfterBackspace(before, after)
  }

  def testRemoveClosingBraceForFunctionBodyWithSingleExpression(): Unit = {
    val before =
      s"""def foo() = {$CARET
         |  someMethod()
         |}
      """.stripMargin
    val after =
      s"""def foo() = $CARET
         |  someMethod()
      """.stripMargin
    doTest(before, after)
  }

  def testRemoveClosingBraceForFunctionBodyWithSingleExpression_1(): Unit = {
    val before =
      s"""def foo() = {${CARET}someMethod()}
      """.stripMargin
    val after =
      s"""def foo() = ${CARET}someMethod()
      """.stripMargin
    doTest(before, after)
  }

  def testRemoveClosingBraceForValInitializingBlockWithSingleExpression(): Unit = {
    val before =
      s"""val x = {$CARET
         |  someMethod()
         |}
      """.stripMargin
    val after =
      s"""val x = $CARET
         |  someMethod()
      """.stripMargin
    doTest(before, after)
  }


  def testRemoveClosingBraceForVarInitializingBlockWithSingleExpression(): Unit = {
    val before =
      s"""var x = {$CARET
         |  someMethod()
         |}
      """.stripMargin
    val after =
      s"""var x = $CARET
         |  someMethod()
      """.stripMargin
    doTest(before, after)
  }

  def testNotRemoveClosingBraceForFunctionBodyWithMultipleExpressions(): Unit = {
    val before =
      s"""def foo() = {$CARET
         |  someMethod1()
         |  someMethod2()
         |}
      """.stripMargin
    val after =
      s"""def foo() = $CARET
         |  someMethod1()
         |  someMethod2()
         |}
      """.stripMargin
    doTest(before, after)
  }

  def testNotRemoveClosingBraceForFunctionBodyWithMultipleExpressionsAndStatements(): Unit = {
    val before =
      s"""def foo() = {$CARET
         |  val x = 42
         |  someMethod()
         |}
      """.stripMargin
    val after =
      s"""def foo() = $CARET
         |  val x = 42
         |  someMethod()
         |}
      """.stripMargin
    doTest(before, after)
  }

  def testNotRemoveClosingBraceForFunctionBodyWithMultipleExpressionsAndStatements_1(): Unit = {
    val before =
      s"""def foo() = {$CARET
         |  try {
         |    someMethod1()
         |  }
         |  someMethod2()
         |}
      """.stripMargin
    val after =
      s"""def foo() = $CARET
         |  try {
         |    someMethod1()
         |  }
         |  someMethod2()
         |}
      """.stripMargin
    doTest(before, after)
  }



}
