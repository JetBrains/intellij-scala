package org.jetbrains.plugins.scala.refactoring.selectExpression

import com.intellij.testFramework.EditorTestUtil.CARET_TAG
import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.base.SimpleTestCase
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaRefactoringUtil
import org.junit.Assert

class SelectExpressionTest extends SimpleTestCase {
  private val caret = CARET_TAG

  private def doTest(@Language("Scala") fileText: String, expectedExpressions: String*): Unit = {
    val (file, offset) = parseText(fileText, CARET_TAG)
    val expressions = ScalaRefactoringUtil.possibleExpressionsToExtract(file, offset)
    Assert.assertArrayEquals(expectedExpressions.toArray[AnyRef], expressions.map(_.getText).toArray[AnyRef])
  }

  private def checkNoExpressions(@Language("Scala") fileText: String): Unit = doTest(fileText)

  def testInfix(): Unit = doTest(s"${caret}1 + 2", "1", "1 + 2")

  def testInfix2(): Unit = doTest(s"1 + ${caret}2", "2", "1 + 2")

  def testNoInfixOp(): Unit = doTest(s"1 ${caret}+ 2", "1 + 2")

  def testMethodCallFromRef(): Unit = doTest(s"${caret}abc(1)", "abc", "abc(1)")

  def testMethodCallFromArg(): Unit = doTest(s"abc(${caret}1)", "1", "abc(1)")

  def testGenericCallNoRef(): Unit = doTest(s"${caret}foo[Int](1)", "foo[Int]", "foo[Int](1)")

  def testNamedArgument(): Unit = doTest(s"foo(${caret}age = 33)", "foo(age = 33)")

  def testTopLevelClassParam(): Unit = checkNoExpressions(s"class A(x: Int = ${caret}42")

  def testSelfInvocationArg(): Unit = checkNoExpressions(
    s"""
       |class A(i: Int) {
       |  def this(s: String) = {
       |    this(${caret}s.length)
       |  }
       |}""".stripMargin)

  def testInterpolatedString(): Unit = doTest(s"""${caret}s"foo"""", """s"foo"""")

  def testInterpolatedStringInjection(): Unit = doTest(s"""s"foo$$${caret}bar"""", "bar", """s"foo$bar"""")

  def testInterpolatedStringBlockInjection(): Unit = doTest(
    s"""val x = s"str $${${caret}1 + 2}"""",
    "1",
    "1 + 2",
    "{1 + 2}",
    s"""s"str $${1 + 2}""""
  )

  def testOneExpressionBlockWithoutBraces(): Unit = doTest(
    s"""1 match {
       |  case 1 => ${caret}"42"
       |}""".stripMargin, """"42"""")

  def testLargeMethodCall(): Unit = doTest(
    s"""foo(1)
       |  ${caret}.bar(2)
       |  .baz(3)
       |""".stripMargin,

    """foo(1)
      |  .bar""".stripMargin,
    """foo(1)
      |  .bar(2)""".stripMargin,
    """foo(1)
      |  .bar(2)
      |  .baz""".stripMargin,
    """foo(1)
      |  .bar(2)
      |  .baz(3)""".stripMargin)

  def testMatchStmt(): Unit = doTest(
    s"""${caret}1 match {
       |  case 0 => "zero"
       |  case 1 => "one"
       |}
       |""".stripMargin,
    "1",
    """1 match {
      |  case 0 => "zero"
      |  case 1 => "one"
      |}""".stripMargin
  )

  def testMatchStmtFromKeyword(): Unit = doTest (
    s"""1 m${caret}atch {
       |  case 0 => "zero"
       |  case 1 => "one"
       |}
       |""".stripMargin,
    """1 match {
      |  case 0 => "zero"
      |  case 1 => "one"
      |}""".stripMargin
  )

  def testMatchStmtFromCase(): Unit = doTest(
    s"""1 match {
       |  ${caret}case 0 => "zero"
       |  case 1 => "one"
       |}
       |""".stripMargin,
    """1 match {
      |  case 0 => "zero"
      |  case 1 => "one"
      |}""".stripMargin
  )

  def testNoMatchStmtFromCaseBlock(): Unit = doTest(
    s"""1 match {
       |  case 0 => ${caret}"zero"
       |  case 1 => "one"
       |}
       |""".stripMargin,
    """"zero""""
  )

  def testIfStmt(): Unit = doTest(
    s"""if (${caret}true) false
       |else true
       |""".stripMargin,
    "true",
    """if (true) false
      |else true""".stripMargin
  )

}