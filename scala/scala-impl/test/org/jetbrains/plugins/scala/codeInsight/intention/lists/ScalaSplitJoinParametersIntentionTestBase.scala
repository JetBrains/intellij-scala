package org.jetbrains.plugins.scala.codeInsight.intention.lists

abstract class ScalaSplitJoinParametersIntentionTestBase extends ScalaSplitJoinLineIntentionTestBase {
  override protected val first: String = "i: Int"
  override protected val second: String = "s: String"

  override protected val noNewLines: String =
    s"""($first,
       | $second)""".stripMargin

  override protected val newLineAfterLeftParen: String =
    s"""(
       |  $first,
       |  $second)""".stripMargin

  override protected val newLineBeforeRightParen: String =
    s"""($first,
       | $second
       |)""".stripMargin

  override protected val newLineAfterLeftParenAndBeforeRightParen: String =
    s"""(
       |  $first,
       |  $second
       |)""".stripMargin

  private def doTestWithMethodParamSettings(newLineAfterLParen: Boolean, newLineBeforeRParen: Boolean)
                                           (singleLineText: String, multiLineText: String): Unit = {
    val settings = getCommonSettings
    val oldLParen = settings.METHOD_PARAMETERS_LPAREN_ON_NEXT_LINE
    val oldRParen = settings.METHOD_PARAMETERS_RPAREN_ON_NEXT_LINE

    try {
      settings.METHOD_PARAMETERS_LPAREN_ON_NEXT_LINE = newLineAfterLParen
      settings.METHOD_PARAMETERS_RPAREN_ON_NEXT_LINE = newLineBeforeRParen

      doTest(
        singleLineText = singleLineText,
        multiLineText = multiLineText
      )
    } finally {
      settings.METHOD_PARAMETERS_LPAREN_ON_NEXT_LINE = oldLParen
      settings.METHOD_PARAMETERS_RPAREN_ON_NEXT_LINE = oldRParen
    }
  }

  private def methodDefBase(args: String): String =
    s"""${indent(s"def foo$CARET" + args, 7)}: Unit = {}
       |""".stripMargin

  private def classDefBase(args: String): String =
    s"""${indent(s"class Foo$CARET" + args, 9)}
       |""".stripMargin

  private def caseClassDefBase(args: String): String =
    s"""${indent(s"case class Foo$CARET" + args, 14)}
       |""".stripMargin

  private val methodDefText = methodDefBase(singleLine)
  private val methodDefTrailingCommaText = methodDefBase(singleLineWithTrailingComma)

  private val classDefText = classDefBase(singleLine)
  private val classDefTrailingCommaText = classDefBase(singleLineWithTrailingComma)

  private val caseClassDefText = caseClassDefBase(singleLine)
  private val caseClassDefTrailingCommaText = caseClassDefBase(singleLineWithTrailingComma)

  // Method Calls

  def testMethodDef1(): Unit =
    doTestWithMethodParamSettings(newLineAfterLParen = false, newLineBeforeRParen = false)(
      singleLineText = methodDefText,
      multiLineText = methodDefBase(noNewLines)
    )

  def testMethodDef2(): Unit =
    doTestWithMethodParamSettings(newLineAfterLParen = false, newLineBeforeRParen = true)(
      singleLineText = methodDefText,
      multiLineText = methodDefBase(newLineBeforeRightParen)
    )

  def testMethodDef3(): Unit =
    doTestWithMethodParamSettings(newLineAfterLParen = true, newLineBeforeRParen = false)(
      singleLineText = methodDefText,
      multiLineText = methodDefBase(newLineAfterLeftParen)
    )

  def testMethodDef4(): Unit =
    doTestWithMethodParamSettings(newLineAfterLParen = true, newLineBeforeRParen = true)(
      singleLineText = methodDefText,
      multiLineText = methodDefBase(newLineAfterLeftParenAndBeforeRightParen)
    )

  def testMethodDefTrailingComma(): Unit =
    doTestWithMethodParamSettings(newLineAfterLParen = false, newLineBeforeRParen = true)(
      singleLineText = methodDefTrailingCommaText,
      multiLineText =
        methodDefBase(
          s"""($first,
             | $second,
             |)""".stripMargin
        )
    )

  def testMethodDefTrailingComma2(): Unit =
    doTestWithMethodParamSettings(newLineAfterLParen = false, newLineBeforeRParen = false)(
      singleLineText = methodDefTrailingCommaText,
      multiLineText =
        methodDefBase(
          s"""($first,
             | $second,)""".stripMargin
        )
    )

  def testMethodDefWithOneArg(): Unit =
    checkIntentionIsNotAvailable(
      s"""def foo$CARET(i: Int): Unit = {}
         |""".stripMargin
    )

  def testMethodDefWithoutArgs(): Unit =
    checkIntentionIsNotAvailable(
      s"""def foo$CARET(): Unit = {}
         |""".stripMargin
    )

  // Class

  def testClass1(): Unit =
    doTestWithMethodParamSettings(newLineAfterLParen = false, newLineBeforeRParen = false)(
      singleLineText = classDefText,
      multiLineText = classDefBase(noNewLines)
    )

  def testClass2(): Unit =
    doTestWithMethodParamSettings(newLineAfterLParen = false, newLineBeforeRParen = true)(
      singleLineText = classDefText,
      multiLineText = classDefBase(newLineBeforeRightParen)
    )

  def testClass3(): Unit =
    doTestWithMethodParamSettings(newLineAfterLParen = true, newLineBeforeRParen = false)(
      singleLineText = classDefText,
      multiLineText = classDefBase(newLineAfterLeftParen)
    )

  def testClass4(): Unit =
    doTestWithMethodParamSettings(newLineAfterLParen = true, newLineBeforeRParen = true)(
      singleLineText = classDefText,
      multiLineText = classDefBase(newLineAfterLeftParenAndBeforeRightParen)
    )

  def testClassTrailingComma(): Unit =
    doTestWithMethodParamSettings(newLineAfterLParen = false, newLineBeforeRParen = true)(
      singleLineText = classDefTrailingCommaText,
      multiLineText =
        classDefBase(
          s"""($first,
             | $second,
             |)""".stripMargin
        )
    )

  def testClassTrailingComma2(): Unit =
    doTestWithMethodParamSettings(newLineAfterLParen = false, newLineBeforeRParen = false)(
      singleLineText = classDefTrailingCommaText,
      multiLineText =
        classDefBase(
          s"""($first,
             | $second,)""".stripMargin
        )
    )

  // Case class

  def testCaseClass1(): Unit =
    doTestWithMethodParamSettings(newLineAfterLParen = false, newLineBeforeRParen = false)(
      singleLineText = caseClassDefText,
      multiLineText = caseClassDefBase(noNewLines)
    )

  def testCaseClass2(): Unit =
    doTestWithMethodParamSettings(newLineAfterLParen = false, newLineBeforeRParen = true)(
      singleLineText = caseClassDefText,
      multiLineText = caseClassDefBase(newLineBeforeRightParen)
    )

  def testCaseClass3(): Unit =
    doTestWithMethodParamSettings(newLineAfterLParen = true, newLineBeforeRParen = false)(
      singleLineText = caseClassDefText,
      multiLineText = caseClassDefBase(newLineAfterLeftParen)
    )

  def testCaseClass4(): Unit =
    doTestWithMethodParamSettings(newLineAfterLParen = true, newLineBeforeRParen = true)(
      singleLineText = caseClassDefText,
      multiLineText = caseClassDefBase(newLineAfterLeftParenAndBeforeRightParen)
    )

  def testCaseClassTrailingComma(): Unit =
    doTestWithMethodParamSettings(newLineAfterLParen = false, newLineBeforeRParen = true)(
      singleLineText = caseClassDefTrailingCommaText,
      multiLineText =
        caseClassDefBase(
          s"""($first,
             | $second,
             |)""".stripMargin
        )
    )

  def testCaseClassTrailingComma2(): Unit =
    doTestWithMethodParamSettings(newLineAfterLParen = false, newLineBeforeRParen = false)(
      singleLineText = caseClassDefTrailingCommaText,
      multiLineText =
        caseClassDefBase(
          s"""($first,
             | $second,)""".stripMargin
        )
    )
}
