package org.jetbrains.plugins.scala.codeInsight.intention.lists

import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings.{NEW_LINE_ALWAYS, NEW_LINE_FOR_MULTIPLE_ARGUMENTS, NO_NEW_LINE}

abstract class ScalaSplitJoinArgumentsIntentionTestBase extends ScalaSplitJoinLineIntentionTestBase {
  override protected val first: String = "42"
  override protected val second: String = """"boo""""

  override protected val noNewLines: String =
    s"""($first,
       |  $second)""".stripMargin

  override protected val newLineAfterLeftParen: String =
    s"""(
       |  $first,
       |  $second)""".stripMargin

  override protected val newLineBeforeRightParen: String =
    s"""($first,
       |  $second
       |)""".stripMargin

  override protected val newLineAfterLeftParenAndBeforeRightParen: String =
    s"""(
       |  $first,
       |  $second
       |)""".stripMargin

  private def doTestWithCallArgsSettings(newLineAfterLParen: Int, newLineBeforeRParen: Boolean)
                                        (singleLineText: String, multiLineText: String): Unit = {
    val commonSettings = getCommonSettings
    val scalaSettings = getScalaSettings
    val oldLParen = scalaSettings.CALL_PARAMETERS_NEW_LINE_AFTER_LPAREN
    val oldRParen = commonSettings.CALL_PARAMETERS_RPAREN_ON_NEXT_LINE

    try {
      scalaSettings.CALL_PARAMETERS_NEW_LINE_AFTER_LPAREN = newLineAfterLParen
      commonSettings.CALL_PARAMETERS_RPAREN_ON_NEXT_LINE = newLineBeforeRParen

      doTest(
        singleLineText = singleLineText,
        multiLineText = multiLineText,
      )
    } finally {
      scalaSettings.CALL_PARAMETERS_NEW_LINE_AFTER_LPAREN = oldLParen
      commonSettings.CALL_PARAMETERS_RPAREN_ON_NEXT_LINE = oldRParen
    }
  }

  private def methodCallBase(args: String): String =
    s"""def foo(i: Int, s: String): Unit = {}
       |
       |object Test {
       |${indent(s"foo$CARET" + args)}
       |}
       |""".stripMargin

  private def classCreationBase(args: String): String =
    s"""class Foo(i: Int, s: String)
       |
       |object Test {
       |${indent(s"new Foo$CARET" + args)}
       |}
       |""".stripMargin

  private def caseClassCreationBase(args: String): String =
    s"""case class Foo(i: Int, s: String)
       |
       |object Test {
       |${indent(s"Foo$CARET" + args)}
       |}
       |""".stripMargin

  private val methodCallText = methodCallBase(singleLine)
  private val methodCallTrailingCommaText = methodCallBase(singleLineWithTrailingComma)

  private val classCreationText = classCreationBase(singleLine)
  private val classCreationTrailingCommaText = classCreationBase(singleLineWithTrailingComma)

  private val caseClassCreationText = caseClassCreationBase(singleLine)
  private val caseClassCreationTrailingCommaText = caseClassCreationBase(singleLineWithTrailingComma)

  // Method Calls

  def testMethodCall1(): Unit =
    doTestWithCallArgsSettings(newLineAfterLParen = NO_NEW_LINE, newLineBeforeRParen = false)(
      singleLineText = methodCallText,
      multiLineText = methodCallBase(noNewLines),
    )

  def testMethodCall2(): Unit =
    doTestWithCallArgsSettings(newLineAfterLParen = NO_NEW_LINE, newLineBeforeRParen = true)(
      singleLineText = methodCallText,
      multiLineText = methodCallBase(newLineBeforeRightParen)
    )

  def testMethodCall3(): Unit =
    doTestWithCallArgsSettings(newLineAfterLParen = NEW_LINE_FOR_MULTIPLE_ARGUMENTS, newLineBeforeRParen = false)(
      singleLineText = methodCallText,
      multiLineText = methodCallBase(newLineAfterLeftParen)
    )

  def testMethodCall4(): Unit =
    doTestWithCallArgsSettings(newLineAfterLParen = NEW_LINE_FOR_MULTIPLE_ARGUMENTS, newLineBeforeRParen = true)(
      singleLineText = methodCallText,
      multiLineText = methodCallBase(newLineAfterLeftParenAndBeforeRightParen)
    )

  def testMethodCall5(): Unit =
    doTestWithCallArgsSettings(newLineAfterLParen = NEW_LINE_ALWAYS, newLineBeforeRParen = false)(
      singleLineText = methodCallText,
      multiLineText = methodCallBase(newLineAfterLeftParen)
    )

  def testMethodCall6(): Unit =
    doTestWithCallArgsSettings(newLineAfterLParen = NEW_LINE_ALWAYS, newLineBeforeRParen = true)(
      singleLineText = methodCallText,
      multiLineText = methodCallBase(newLineAfterLeftParenAndBeforeRightParen)
    )

  def testMethodCallTrailingComma(): Unit =
    doTestWithCallArgsSettings(newLineAfterLParen = NO_NEW_LINE, newLineBeforeRParen = true)(
      singleLineText = methodCallTrailingCommaText,
      multiLineText =
        methodCallBase(
          s"""($first,
             |  $second,
             |)""".stripMargin
        )
    )

  def testMethodCallTrailingComma2(): Unit =
    doTestWithCallArgsSettings(newLineAfterLParen = NO_NEW_LINE, newLineBeforeRParen = false)(
      singleLineText = methodCallTrailingCommaText,
      multiLineText =
        methodCallBase(
          s"""($first,
             |  $second,)""".stripMargin
        )
    )

  def testPatternCall(): Unit = {
    val args =
      if (testType.isJoin)
        """(
          |    "...",
          |    true
          |  )""".stripMargin
      else """("...", true)"""
    checkIntentionIsAvailable(
      s"""def foo(i: Int, s: String, b: Boolean): Unit = {}
         |
         |object Test {
         |  val pattern = foo($first, _, _)
         |  pattern$CARET$args
         |}
         |""".stripMargin
    )
  }

  def testMethodCallWithOneArg(): Unit =
    checkIntentionIsNotAvailable(
      s"""def foo(i: Int): Unit = {}
         |
         |object Test {
         |  foo$CARET($first)
         |}""".stripMargin
    )

  def testMethodCallWithoutArgs(): Unit =
    checkIntentionIsNotAvailable(
      s"""def foo(): Unit = {}
         |
         |object Test {
         |  foo$CARET()
         |}""".stripMargin
    )

  // Class creation

  def testClassCreation1(): Unit =
    doTestWithCallArgsSettings(newLineAfterLParen = NO_NEW_LINE, newLineBeforeRParen = false)(
      singleLineText = classCreationText,
      multiLineText = classCreationBase(noNewLines)
    )

  def testClassCreation2(): Unit =
    doTestWithCallArgsSettings(newLineAfterLParen = NO_NEW_LINE, newLineBeforeRParen = true)(
      singleLineText = classCreationText,
      multiLineText = classCreationBase(newLineBeforeRightParen)
    )

  def testClassCreation3(): Unit =
    doTestWithCallArgsSettings(newLineAfterLParen = NEW_LINE_FOR_MULTIPLE_ARGUMENTS, newLineBeforeRParen = false)(
      singleLineText = classCreationText,
      multiLineText = classCreationBase(newLineAfterLeftParen)
    )

  def testClassCreation4(): Unit =
    doTestWithCallArgsSettings(newLineAfterLParen = NEW_LINE_FOR_MULTIPLE_ARGUMENTS, newLineBeforeRParen = true)(
      singleLineText = classCreationText,
      multiLineText = classCreationBase(newLineAfterLeftParenAndBeforeRightParen)
    )

  def testClassCreation5(): Unit =
    doTestWithCallArgsSettings(newLineAfterLParen = NEW_LINE_ALWAYS, newLineBeforeRParen = false)(
      singleLineText = classCreationText,
      multiLineText = classCreationBase(newLineAfterLeftParen)
    )

  def testClassCreation6(): Unit =
    doTestWithCallArgsSettings(newLineAfterLParen = NEW_LINE_ALWAYS, newLineBeforeRParen = true)(
      singleLineText = classCreationText,
      multiLineText = classCreationBase(newLineAfterLeftParenAndBeforeRightParen)
    )

  def testClassCreationTrailingComma(): Unit =
    doTestWithCallArgsSettings(newLineAfterLParen = NO_NEW_LINE, newLineBeforeRParen = true)(
      singleLineText = classCreationTrailingCommaText,
      multiLineText =
        classCreationBase(
          s"""($first,
             |  $second,
             |)""".stripMargin
        )
    )

  def testClassCreationTrailingComma2(): Unit =
    doTestWithCallArgsSettings(newLineAfterLParen = NO_NEW_LINE, newLineBeforeRParen = false)(
      singleLineText = classCreationTrailingCommaText,
      multiLineText =
        classCreationBase(
          s"""($first,
             |  $second,)""".stripMargin
        )
    )

  // Case class creation

  def testCaseClassCreation1(): Unit =
    doTestWithCallArgsSettings(newLineAfterLParen = NO_NEW_LINE, newLineBeforeRParen = false)(
      singleLineText = caseClassCreationText,
      multiLineText = caseClassCreationBase(noNewLines)
    )

  def testCaseClassCreation2(): Unit =
    doTestWithCallArgsSettings(newLineAfterLParen = NO_NEW_LINE, newLineBeforeRParen = true)(
      singleLineText = caseClassCreationText,
      multiLineText = caseClassCreationBase(newLineBeforeRightParen)
    )

  def testCaseClassCreation3(): Unit =
    doTestWithCallArgsSettings(newLineAfterLParen = NEW_LINE_FOR_MULTIPLE_ARGUMENTS, newLineBeforeRParen = false)(
      singleLineText = caseClassCreationText,
      multiLineText = caseClassCreationBase(newLineAfterLeftParen)
    )

  def testCaseClassCreation4(): Unit =
    doTestWithCallArgsSettings(newLineAfterLParen = NEW_LINE_FOR_MULTIPLE_ARGUMENTS, newLineBeforeRParen = true)(
      singleLineText = caseClassCreationText,
      multiLineText = caseClassCreationBase(newLineAfterLeftParenAndBeforeRightParen)
    )

  def testCaseClassCreation5(): Unit =
    doTestWithCallArgsSettings(newLineAfterLParen = NEW_LINE_ALWAYS, newLineBeforeRParen = false)(
      singleLineText = caseClassCreationText,
      multiLineText = caseClassCreationBase(newLineAfterLeftParen)
    )

  def testCaseClassCreation6(): Unit =
    doTestWithCallArgsSettings(newLineAfterLParen = NEW_LINE_ALWAYS, newLineBeforeRParen = true)(
      singleLineText = caseClassCreationText,
      multiLineText = caseClassCreationBase(newLineAfterLeftParenAndBeforeRightParen)
    )

  def testCaseClassCreationTrailingComma(): Unit =
    doTestWithCallArgsSettings(newLineAfterLParen = NO_NEW_LINE, newLineBeforeRParen = true)(
      singleLineText = caseClassCreationTrailingCommaText,
      multiLineText =
        caseClassCreationBase(
          s"""($first,
             |  $second,
             |)""".stripMargin
        )
    )

  def testCaseClassCreationTrailingComma2(): Unit =
    doTestWithCallArgsSettings(newLineAfterLParen = NO_NEW_LINE, newLineBeforeRParen = false)(
      singleLineText = caseClassCreationTrailingCommaText,
      multiLineText =
        caseClassCreationBase(
          s"""($first,
             |  $second,)""".stripMargin
        )
    )
}
