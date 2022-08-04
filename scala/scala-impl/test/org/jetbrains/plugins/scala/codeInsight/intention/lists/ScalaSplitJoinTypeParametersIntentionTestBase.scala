package org.jetbrains.plugins.scala.codeInsight.intention.lists

abstract class ScalaSplitJoinTypeParametersIntentionTestBase extends ScalaSplitJoinLineIntentionTestBase {
  private def doTest(singleLineText: String, multiLineText: String): Unit =
    doTest(singleLineText, multiLineText, listStartChar = '[')

  // Methods

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

  def testMethodWithOneArg(): Unit =
    checkIntentionIsNotAvailable("def foo[A]: Unit = {}")

  def testMethodWithOneArgTrailingComma(): Unit =
    checkIntentionIsNotAvailable("def foo[A, ]: Unit = {}")

  // Class

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

  def testClassWithOneArg(): Unit =
    checkIntentionIsNotAvailable("class Foo[A]")

  def testClassWithOneArgTrailingComma(): Unit =
    checkIntentionIsNotAvailable("class Foo[A, ]")

  // Case class

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

  def testCaseClassWithOneArg(): Unit =
    checkIntentionIsNotAvailable("case class Foo[A]()")

  def testCaseClassWithOneArgTrailingComma(): Unit =
    checkIntentionIsNotAvailable("case class Foo[A, ]()")
}
