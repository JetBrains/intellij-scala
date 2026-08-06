package org.jetbrains.plugins.scala.codeInsight.intention.lists

import org.junit.Test

abstract class ScalaSplitJoinTypeParametersIntentionTestBase extends ScalaSplitJoinLineIntentionTestBase {
  private def doTest(singleLineText: String, multiLineText: String): Unit =
    doTest(singleLineText, multiLineText, listStartChar = '[')

  // Methods

  @Test
  def testMethod(): Unit =
    doTest(
      singleLineText =
        """def foo[A, B]: Unit = {}""",
      multiLineText =
        """def foo[
          |  A,
          |  B
          |]: Unit = {}""".stripMargin
    )

  @Test
  def testMethodTrailingComma(): Unit =
    doTest(
      singleLineText =
        """def foo[A, B, ]: Unit = {}""",
      multiLineText =
        """def foo[
          |  A,
          |  B,
          |]: Unit = {}""".stripMargin
    )

  @Test
  def testMethodWithOneArg(): Unit =
    checkIntentionIsNotAvailable("def foo[A]: Unit = {}")

  @Test
  def testMethodWithOneArgTrailingComma(): Unit =
    checkIntentionIsNotAvailable("def foo[A, ]: Unit = {}")

  // Class

  @Test
  def testClass(): Unit =
    doTest(
      singleLineText =
        """class Foo[A, B]""",
      multiLineText =
        """class Foo[
          |  A,
          |  B
          |]""".stripMargin
    )

  @Test
  def testClassTrailingComma(): Unit =
    doTest(
      singleLineText =
        """class Foo[A, B, ]""",
      multiLineText =
        """class Foo[
          |  A,
          |  B,
          |]""".stripMargin
    )

  @Test
  def testClassWithOneArg(): Unit =
    checkIntentionIsNotAvailable("class Foo[A]")

  @Test
  def testClassWithOneArgTrailingComma(): Unit =
    checkIntentionIsNotAvailable("class Foo[A, ]")

  // Case class

  @Test
  def testCaseCreation(): Unit =
    doTest(
      singleLineText =
        """case class Foo[A, B]()""",
      multiLineText =
        """case class Foo[
          |  A,
          |  B
          |]()""".stripMargin
    )

  @Test
  def testCaseClassTrailingComma(): Unit =
    doTest(
      singleLineText =
        """case class Foo[A, B, ]()""",
      multiLineText =
        """case class Foo[
          |  A,
          |  B,
          |]()""".stripMargin
    )

  @Test
  def testCaseClassWithOneArg(): Unit =
    checkIntentionIsNotAvailable("case class Foo[A]()")

  @Test
  def testCaseClassWithOneArgTrailingComma(): Unit =
    checkIntentionIsNotAvailable("case class Foo[A, ]()")
}
