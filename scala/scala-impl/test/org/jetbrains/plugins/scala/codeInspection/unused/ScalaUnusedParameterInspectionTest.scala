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

  def testPublicMethod(): Unit = checkTextHasNoErrors(
    """
      |object Global {
      |  def test(a: Int): Unit = ()
      |}
      |""".stripMargin)

  def testNotInCaseClass(): Unit = checkTextHasNoErrors(
    "case class Test(a: Int)"
  )

  def testCaseClassSndClause(): Unit = checkTextHasError(
    s"case class Test(a: Int)($p: Int)"
  )

  ///////// normal class parameter /////////
  def testUnusedPrivateClass(): Unit = checkTextHasError(
    s"""
       |object Global {
       |  private class Test($p: Int)
       |}
       |""".stripMargin
  )

  def testUnusedPublicClass(): Unit = checkTextHasError(
    s"""
       |object Global {
       |  class Test($p: Int)
       |}
       |""".stripMargin
  )

  ///////// val class parameter /////////
  def testUnusedPrivateClassVal(): Unit = checkTextHasError(
    s"""
       |object Global {
       |  private class Test(val $p: Int)
       |}
       |""".stripMargin
  )

  def testUnusedPublicClassVal(): Unit = checkTextHasNoErrors(
    s"""
       |object Global {
       |  class Test(val p: Int)
       |}
       |""".stripMargin
  )

  def testUnusedPublicClassPrivateVal(): Unit = checkTextHasError(
    s"""
       |object Global {
       |  class Test(private val $p: Int)
       |}
       |""".stripMargin
  )

  def testUsedPrivateClassVal(): Unit = checkTextHasNoErrors(
    s"""
       |object Global {
       |  private class Test(val p: Int)
       |  val x = new Test(3)
       |  println(x.p)
       |}
       |""".stripMargin
  )


  ///////// case class parameter /////////
  def testUnusedPrivateCaseClass(): Unit = checkTextHasError(
    s"""
       |object Global {
       |  private case class Test($p: Int)
       |}
       |""".stripMargin
  )

  def testUnusedPublicCaseClass(): Unit = checkTextHasNoErrors(
    s"""
       |object Global {
       |  case class Test(p: Int)
       |}
       |""".stripMargin
  )

  def testUsedPrivateCaseClass(): Unit = checkTextHasNoErrors(
    s"""
       |object Global {
       |  private case class Test(p: Int)
       |  val x = new Test(3)
       |  println(x.p)
       |}
       |""".stripMargin
  )

  // inheritance stuff
  def testOverrideWithVal(): Unit = checkTextHasNoErrors(
    """
      |object Global {
      |  trait Base {
      |    def p: Int
      |  }
      |  private class Test(val p: Int) extends Base
      |}
      |""".stripMargin
  )

  def testOverrideWithCaseClass(): Unit = checkTextHasNoErrors(
    """
      |object Global {
      |  trait Base {
      |    def p: Int
      |  }
      |  private case class Test(p: Int) extends Base
      |}
      |""".stripMargin
  )
}
