package org.jetbrains.plugins.scala.codeInspection.unused

import com.intellij.testFramework.EditorTestUtil

class ScalaUnusedParameterInspectionTest extends ScalaUnusedSymbolInspectionTestBase {

  import EditorTestUtil.{SELECTION_END_TAG => END, SELECTION_START_TAG => START}

  private val p = START + "p" + END

  private def doFunctionParameterTest(beforeClause: String, afterClause: String, argsBefore: String, argsAfter: String): Unit = {
    val paramsPlaceholder = "<params-placeholder>"
    val argsPlaceholder = "<args-placeholder>"
    val code =
      s"""
         |class Foo {
         |  val a = 0
         |  val b = 0
         |  private def test$paramsPlaceholder: Int = {
         |    // a, b should neither be unused nor unresolvable
         |    a + b
         |  }
         |
         |  val c = a + b
         |  test$argsPlaceholder
         |}
      """.stripMargin
    checkTextHasError(code.replace(paramsPlaceholder, beforeClause).replace(argsPlaceholder, argsBefore))

    val rawArgsBefore = beforeClause.replace(START, "").replace(END, "")
    val before = code.replace(paramsPlaceholder, rawArgsBefore).replace(argsPlaceholder, argsBefore)
    val after = code.replace(paramsPlaceholder, afterClause).replace(argsPlaceholder, argsAfter)
    testQuickFix(before, after, hint)
  }

  private def doConstructorParameterTest(beforeClause: String, afterClause: String, argsBefore: String, argsAfter: String): Unit = {
    val paramsPlaceholder = "<params-placeholder>"
    val argsPlaceholder = "<args-placeholder>"
    val code =
      s"""
         |class Foo {
         |  val a = 0
         |  val b = 0
         |  class Test$paramsPlaceholder {
         |    // a, b should neither be unused nor unresolvable
         |    a + b
         |  }
         |
         |  val c = a + b
         |  new Test$argsPlaceholder
         |}
      """.stripMargin
    checkTextHasError(code.replace(paramsPlaceholder, beforeClause).replace(argsPlaceholder, argsBefore))

    val rawArgsBefore = beforeClause.replace(START, "").replace(END, "")
    val before = code.replace(paramsPlaceholder, rawArgsBefore).replace(argsPlaceholder, argsBefore)
    val after = code.replace(paramsPlaceholder, afterClause).replace(argsPlaceholder, argsAfter)
    testQuickFix(before, after, hint)
  }

  private def doTest(beforeClause: String, afterClause: String, argsBefore: String, argsAfter: String): Unit = {
    doFunctionParameterTest(beforeClause, afterClause, argsBefore, argsAfter)
    doConstructorParameterTest(beforeClause, afterClause, argsBefore, argsAfter)
  }

  def testEasyLast(): Unit =
    doTest(s"(a: Int, $p: Int)", "(a: Int)",
           s"(1, 2)",            "(1)")

  def testEasyFirst(): Unit =
    doTest(s"($p: Int, a: Int)", "(a: Int)",
           s"(1, 2)",            "(2)")

  def testEmptyAfter(): Unit = {
    doTest(s"($p: Int)", "()",
           s"(1)",       "()")
  }

  def testMultipleClauses(): Unit =
    doTest(s"(a: Int, $p: Int)(b: Int)", "(a: Int)(b: Int)",
            "(1, 2)(3)",                 "(1)(3)")

  def testMultipleClausesEmptyAfter(): Unit =
    doTest(s"(a: Int)($p: Int)", "(a: Int)",
            "(1)(2)",            "(1)")


  def testMultipleClausesEmptyAfter2(): Unit =
    doTest(s"($p: Int)(a: Int)", "(a: Int)",
            "(1)(2)",            "(2)")
}
