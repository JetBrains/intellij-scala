package org.jetbrains.plugins.scala.codeInsight.intention.lists

abstract class ScalaSplitJoinParametersIntentionTestBase extends ScalaSplitJoinLineIntentionTestBase {
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
        multiLineText = multiLineText,
        listStartChar = '(',
      )
    } finally {
      settings.METHOD_PARAMETERS_LPAREN_ON_NEXT_LINE = oldLParen
      settings.METHOD_PARAMETERS_RPAREN_ON_NEXT_LINE = oldRParen
    }
  }

  // Method Calls

  def testMethodDef1(): Unit =
    doTestWithMethodParamSettings(newLineAfterLParen = false, newLineBeforeRParen = false)(
      singleLineText =
        """def foo(i: Int, s: String)""",
      multiLineText =
        """def foo(i: Int,
          |        s: String)""".stripMargin
    )

  def testMethodDef2(): Unit =
    doTestWithMethodParamSettings(newLineAfterLParen = false, newLineBeforeRParen = true)(
      singleLineText =
        """def foo(i: Int, s: String)""",
      multiLineText =
        """def foo(i: Int,
          |        s: String
          |       )""".stripMargin
    )

  def testMethodDef3(): Unit =
    doTestWithMethodParamSettings(newLineAfterLParen = true, newLineBeforeRParen = false)(
      singleLineText =
        """def foo(i: Int, s: String)""",
      multiLineText =
        """def foo(
          |         i: Int,
          |         s: String)""".stripMargin
    )

  def testMethodDef4(): Unit =
    doTestWithMethodParamSettings(newLineAfterLParen = true, newLineBeforeRParen = true)(
      singleLineText =
        """def foo(i: Int, s: String)""",
      multiLineText =
        """def foo(
          |         i: Int,
          |         s: String
          |       )""".stripMargin
    )

  def testMethodDefTrailingComma(): Unit =
    doTestWithMethodParamSettings(newLineAfterLParen = false, newLineBeforeRParen = true)(
      singleLineText =
        """def foo(i: Int, s: String,)""",
      multiLineText =
        """def foo(i: Int,
          |        s: String,
          |       )""".stripMargin
    )

  def testMethodDefTrailingComma2(): Unit =
    doTestWithMethodParamSettings(newLineAfterLParen = false, newLineBeforeRParen = false)(
      singleLineText =
        """def foo(i: Int, s: String,)""",
      multiLineText =
        """def foo(i: Int,
          |        s: String,)""".stripMargin
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
      singleLineText =
        """class Foo(i: Int, s: String)""",
      multiLineText =
        """class Foo(i: Int,
          |          s: String)""".stripMargin
    )

  def testClass2(): Unit =
    doTestWithMethodParamSettings(newLineAfterLParen = false, newLineBeforeRParen = true)(
      singleLineText =
        """class Foo(i: Int, s: String)""",
      multiLineText =
        """class Foo(i: Int,
          |          s: String
          |         )""".stripMargin
    )

  def testClass3(): Unit =
    doTestWithMethodParamSettings(newLineAfterLParen = true, newLineBeforeRParen = false)(
      singleLineText =
        """class Foo(i: Int, s: String)""",
      multiLineText =
        """class Foo(
          |           i: Int,
          |           s: String)""".stripMargin
    )

  def testClass4(): Unit =
    doTestWithMethodParamSettings(newLineAfterLParen = true, newLineBeforeRParen = true)(
      singleLineText =
        """class Foo(i: Int, s: String)""",
      multiLineText =
        """class Foo(
          |           i: Int,
          |           s: String
          |         )""".stripMargin
    )

  def testClassTrailingComma(): Unit =
    doTestWithMethodParamSettings(newLineAfterLParen = false, newLineBeforeRParen = true)(
      singleLineText =
        """class Foo(i: Int, s: String,)""",
      multiLineText =
        s"""class Foo(i: Int,
           |          s: String,
           |         )""".stripMargin
    )

  def testClassTrailingComma2(): Unit =
    doTestWithMethodParamSettings(newLineAfterLParen = false, newLineBeforeRParen = false)(
      singleLineText =
        """class Foo(i: Int, s: String,)""",
      multiLineText =
        s"""class Foo(i: Int,
           |          s: String,)""".stripMargin
    )

  // Case class

  def testCaseClass1(): Unit =
    doTestWithMethodParamSettings(newLineAfterLParen = false, newLineBeforeRParen = false)(
      singleLineText =
        "case class Foo(i: Int, s: String)",
      multiLineText =
        """case class Foo(i: Int,
          |               s: String)""".stripMargin
    )

  def testCaseClass2(): Unit =
    doTestWithMethodParamSettings(newLineAfterLParen = false, newLineBeforeRParen = true)(
      singleLineText =
        "case class Foo(i: Int, s: String)",
      multiLineText =
        """case class Foo(i: Int,
          |               s: String
          |              )""".stripMargin

    )

  def testCaseClass3(): Unit =
    doTestWithMethodParamSettings(newLineAfterLParen = true, newLineBeforeRParen = false)(
      singleLineText =
        "case class Foo(i: Int, s: String)",
      multiLineText =
        """case class Foo(
          |                i: Int,
          |                s: String)""".stripMargin
    )

  def testCaseClass4(): Unit =
    doTestWithMethodParamSettings(newLineAfterLParen = true, newLineBeforeRParen = true)(
      singleLineText =
        "case class Foo(i: Int, s: String)",
      multiLineText =
        """case class Foo(
          |                i: Int,
          |                s: String
          |              )""".stripMargin
    )

  def testCaseClassTrailingComma(): Unit =
    doTestWithMethodParamSettings(newLineAfterLParen = false, newLineBeforeRParen = true)(
      singleLineText = "case class Foo(i: Int, s: String,)",
      multiLineText =
        s"""case class Foo(i: Int,
           |               s: String,
           |              )""".stripMargin
    )

  def testCaseClassTrailingComma2(): Unit =
    doTestWithMethodParamSettings(newLineAfterLParen = false, newLineBeforeRParen = false)(
      singleLineText = "case class Foo(i: Int, s: String,)",
      multiLineText =
        s"""case class Foo(i: Int,
           |               s: String,)""".stripMargin
    )
}
