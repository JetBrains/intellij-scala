package org.jetbrains.plugins.scala.codeInsight.intention.lists

import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings.{NEW_LINE_ALWAYS, NEW_LINE_FOR_MULTIPLE_ARGUMENTS, NO_NEW_LINE}

abstract class ScalaSplitJoinArgumentsIntentionTestBase extends ScalaSplitJoinLineIntentionTestBase {
  private def doTestWithCallArgsSettings(newLineAfterLParen: Int, newLineBeforeRParen: Boolean)
                                        (singleLineText: String, multiLineText: String): Unit = {
    val commonSettings = getCommonCodeStyleSettings
    val scalaSettings = getScalaCodeStyleSettings
    val oldLParen = scalaSettings.CALL_PARAMETERS_NEW_LINE_AFTER_LPAREN
    val oldRParen = commonSettings.CALL_PARAMETERS_RPAREN_ON_NEXT_LINE

    try {
      scalaSettings.CALL_PARAMETERS_NEW_LINE_AFTER_LPAREN = newLineAfterLParen
      commonSettings.CALL_PARAMETERS_RPAREN_ON_NEXT_LINE = newLineBeforeRParen

      doTest(
        singleLineText = singleLineText,
        multiLineText = multiLineText,
        listStartChar = '(',
      )
    } finally {
      scalaSettings.CALL_PARAMETERS_NEW_LINE_AFTER_LPAREN = oldLParen
      commonSettings.CALL_PARAMETERS_RPAREN_ON_NEXT_LINE = oldRParen
    }
  }

  // Method Calls

  def testMethodCall1(): Unit =
    doTestWithCallArgsSettings(newLineAfterLParen = NO_NEW_LINE, newLineBeforeRParen = false)(
      singleLineText =
        """def foo(i: Int, s: String): Unit = {}
          |
          |object Test {
          |  foo(42, "boo")
          |}""".stripMargin,
      multiLineText =
        """def foo(i: Int, s: String): Unit = {}
          |
          |object Test {
          |  foo(42,
          |    "boo")
          |}""".stripMargin
    )

  def testMethodCall2(): Unit =
    doTestWithCallArgsSettings(newLineAfterLParen = NO_NEW_LINE, newLineBeforeRParen = true)(
      singleLineText =
        """def foo(i: Int, s: String): Unit = {}
          |
          |object Test {
          |  foo(42, "boo")
          |}""".stripMargin,
      multiLineText =
        """def foo(i: Int, s: String): Unit = {}
          |
          |object Test {
          |  foo(42,
          |    "boo"
          |  )
          |}""".stripMargin
    )

  def testMethodCall3(): Unit =
    doTestWithCallArgsSettings(newLineAfterLParen = NEW_LINE_FOR_MULTIPLE_ARGUMENTS, newLineBeforeRParen = false)(
      singleLineText =
        """def foo(i: Int, s: String): Unit = {}
          |
          |object Test {
          |  foo(42, "boo")
          |}""".stripMargin,
      multiLineText =
        """def foo(i: Int, s: String): Unit = {}
          |
          |object Test {
          |  foo(
          |    42,
          |    "boo")
          |}""".stripMargin
    )

  def testMethodCall4(): Unit =
    doTestWithCallArgsSettings(newLineAfterLParen = NEW_LINE_FOR_MULTIPLE_ARGUMENTS, newLineBeforeRParen = true)(
      singleLineText =
        """def foo(i: Int, s: String): Unit = {}
          |
          |object Test {
          |  foo(42, "boo")
          |}""".stripMargin,
      multiLineText =
        """def foo(i: Int, s: String): Unit = {}
          |
          |object Test {
          |  foo(
          |    42,
          |    "boo"
          |  )
          |}""".stripMargin
    )

  def testMethodCall5(): Unit =
    doTestWithCallArgsSettings(newLineAfterLParen = NEW_LINE_ALWAYS, newLineBeforeRParen = false)(
      singleLineText =
        """def foo(i: Int, s: String): Unit = {}
          |
          |object Test {
          |  foo(42, "boo")
          |}""".stripMargin,
      multiLineText =
        """def foo(i: Int, s: String): Unit = {}
          |
          |object Test {
          |  foo(
          |    42,
          |    "boo")
          |}""".stripMargin,
    )

  def testMethodCall6(): Unit =
    doTestWithCallArgsSettings(newLineAfterLParen = NEW_LINE_ALWAYS, newLineBeforeRParen = true)(
      singleLineText =
        """def foo(i: Int, s: String): Unit = {}
          |
          |object Test {
          |  foo(42, "boo")
          |}""".stripMargin,
      multiLineText =
        """def foo(i: Int, s: String): Unit = {}
          |
          |object Test {
          |  foo(
          |    42,
          |    "boo"
          |  )
          |}""".stripMargin,
    )

  def testMethodCallTrailingComma(): Unit =
    doTestWithCallArgsSettings(newLineAfterLParen = NO_NEW_LINE, newLineBeforeRParen = true)(
      singleLineText =
        """def foo(i: Int, s: String): Unit = {}
          |
          |object Test {
          |  foo(42, "boo",)
          |}""".stripMargin,
      multiLineText =
        """def foo(i: Int, s: String): Unit = {}
          |
          |object Test {
          |  foo(42,
          |    "boo",
          |  )
          |}""".stripMargin
    )

  def testMethodCallTrailingComma2(): Unit =
    doTestWithCallArgsSettings(newLineAfterLParen = NO_NEW_LINE, newLineBeforeRParen = false)(
      singleLineText =
        """def foo(i: Int, s: String): Unit = {}
          |
          |object Test {
          |  foo(42, "boo",)
          |}""".stripMargin,
      multiLineText =
        """def foo(i: Int, s: String): Unit = {}
          |
          |object Test {
          |  foo(42,
          |    "boo",)
          |}""".stripMargin
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
         |  val pattern = foo(42, _, _)
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
         |  foo$CARET(42)
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
      singleLineText =
        """class Foo(i: Int, s: String)
          |
          |object Test {
          |  new Foo(42, "boo")
          |}""".stripMargin,
      multiLineText =
        """class Foo(i: Int, s: String)
          |
          |object Test {
          |  new Foo(42,
          |    "boo")
          |}""".stripMargin
    )

  def testClassCreation2(): Unit =
    doTestWithCallArgsSettings(newLineAfterLParen = NO_NEW_LINE, newLineBeforeRParen = true)(
      singleLineText =
        """class Foo(i: Int, s: String)
          |
          |object Test {
          |  new Foo(42, "boo")
          |}""".stripMargin,
      multiLineText =
        """class Foo(i: Int, s: String)
          |
          |object Test {
          |  new Foo(42,
          |    "boo"
          |  )
          |}""".stripMargin
    )

  def testClassCreation3(): Unit =
    doTestWithCallArgsSettings(newLineAfterLParen = NEW_LINE_FOR_MULTIPLE_ARGUMENTS, newLineBeforeRParen = false)(
      singleLineText =
        """class Foo(i: Int, s: String)
          |
          |object Test {
          |  new Foo(42, "boo")
          |}""".stripMargin,
      multiLineText =
        """class Foo(i: Int, s: String)
          |
          |object Test {
          |  new Foo(
          |    42,
          |    "boo")
          |}""".stripMargin
    )

  def testClassCreation4(): Unit =
    doTestWithCallArgsSettings(newLineAfterLParen = NEW_LINE_FOR_MULTIPLE_ARGUMENTS, newLineBeforeRParen = true)(
      singleLineText =
        """class Foo(i: Int, s: String)
          |
          |object Test {
          |  new Foo(42, "boo")
          |}""".stripMargin,
      multiLineText =
        """class Foo(i: Int, s: String)
          |
          |object Test {
          |  new Foo(
          |    42,
          |    "boo"
          |  )
          |}""".stripMargin
    )

  def testClassCreation5(): Unit =
    doTestWithCallArgsSettings(newLineAfterLParen = NEW_LINE_ALWAYS, newLineBeforeRParen = false)(
      singleLineText =
        """class Foo(i: Int, s: String)
          |
          |object Test {
          |  new Foo(42, "boo")
          |}""".stripMargin,
      multiLineText =
        """class Foo(i: Int, s: String)
          |
          |object Test {
          |  new Foo(
          |    42,
          |    "boo")
          |}""".stripMargin
    )

  def testClassCreation6(): Unit =
    doTestWithCallArgsSettings(newLineAfterLParen = NEW_LINE_ALWAYS, newLineBeforeRParen = true)(
      singleLineText =
        """class Foo(i: Int, s: String)
          |
          |object Test {
          |  new Foo(42, "boo")
          |}""".stripMargin,
      multiLineText =
        """class Foo(i: Int, s: String)
          |
          |object Test {
          |  new Foo(
          |    42,
          |    "boo"
          |  )
          |}""".stripMargin
    )

  def testClassCreationTrailingComma(): Unit =
    doTestWithCallArgsSettings(newLineAfterLParen = NO_NEW_LINE, newLineBeforeRParen = true)(
      singleLineText =
        """class Foo(i: Int, s: String)
          |
          |object Test {
          |  new Foo(42, "boo",)
          |}""".stripMargin,
      multiLineText =
        """class Foo(i: Int, s: String)
          |
          |object Test {
          |  new Foo(42,
          |    "boo",
          |  )
          |}""".stripMargin
    )

  def testClassCreationTrailingComma2(): Unit =
    doTestWithCallArgsSettings(newLineAfterLParen = NO_NEW_LINE, newLineBeforeRParen = false)(
      singleLineText =
        """class Foo(i: Int, s: String)
          |
          |object Test {
          |  new Foo(42, "boo",)
          |}""".stripMargin,
      multiLineText =
        """class Foo(i: Int, s: String)
          |
          |object Test {
          |  new Foo(42,
          |    "boo",)
          |}""".stripMargin
    )

  // Case class creation

  def testCaseClassCreation1(): Unit =
    doTestWithCallArgsSettings(newLineAfterLParen = NO_NEW_LINE, newLineBeforeRParen = false)(
      singleLineText =
        """case class Foo(i: Int, s: String)
          |
          |object Test {
          |  Foo(42, "boo")
          |}""".stripMargin,
      multiLineText =
        """case class Foo(i: Int, s: String)
          |
          |object Test {
          |  Foo(42,
          |    "boo")
          |}""".stripMargin
    )

  def testCaseClassCreation2(): Unit =
    doTestWithCallArgsSettings(newLineAfterLParen = NO_NEW_LINE, newLineBeforeRParen = true)(
      singleLineText =
        """case class Foo(i: Int, s: String)
          |
          |object Test {
          |  Foo(42, "boo")
          |}""".stripMargin,
      multiLineText =
        """case class Foo(i: Int, s: String)
          |
          |object Test {
          |  Foo(42,
          |    "boo"
          |  )
          |}""".stripMargin
    )

  def testCaseClassCreation3(): Unit =
    doTestWithCallArgsSettings(newLineAfterLParen = NEW_LINE_FOR_MULTIPLE_ARGUMENTS, newLineBeforeRParen = false)(
      singleLineText =
        """case class Foo(i: Int, s: String)
          |
          |object Test {
          |  Foo(42, "boo")
          |}""".stripMargin,
      multiLineText =
        """case class Foo(i: Int, s: String)
          |
          |object Test {
          |  Foo(
          |    42,
          |    "boo")
          |}""".stripMargin
    )

  def testCaseClassCreation4(): Unit =
    doTestWithCallArgsSettings(newLineAfterLParen = NEW_LINE_FOR_MULTIPLE_ARGUMENTS, newLineBeforeRParen = true)(
      singleLineText =
        """case class Foo(i: Int, s: String)
          |
          |object Test {
          |  Foo(42, "boo")
          |}""".stripMargin,
      multiLineText =
        """case class Foo(i: Int, s: String)
          |
          |object Test {
          |  Foo(
          |    42,
          |    "boo"
          |  )
          |}""".stripMargin
    )

  def testCaseClassCreation5(): Unit =
    doTestWithCallArgsSettings(newLineAfterLParen = NEW_LINE_ALWAYS, newLineBeforeRParen = false)(
      singleLineText =
        """case class Foo(i: Int, s: String)
          |
          |object Test {
          |  Foo(42, "boo")
          |}""".stripMargin,
      multiLineText =
        """case class Foo(i: Int, s: String)
          |
          |object Test {
          |  Foo(
          |    42,
          |    "boo")
          |}""".stripMargin
    )

  def testCaseClassCreation6(): Unit =
    doTestWithCallArgsSettings(newLineAfterLParen = NEW_LINE_ALWAYS, newLineBeforeRParen = true)(
      singleLineText =
        """case class Foo(i: Int, s: String)
          |
          |object Test {
          |  Foo(42, "boo")
          |}""".stripMargin,
      multiLineText =
        """case class Foo(i: Int, s: String)
          |
          |object Test {
          |  Foo(
          |    42,
          |    "boo"
          |  )
          |}""".stripMargin
    )

  def testCaseClassCreationTrailingComma(): Unit =
    doTestWithCallArgsSettings(newLineAfterLParen = NO_NEW_LINE, newLineBeforeRParen = true)(
      singleLineText =
        """case class Foo(i: Int, s: String)
          |
          |object Test {
          |  Foo(42, "boo",)
          |}""".stripMargin,
      multiLineText =
        """case class Foo(i: Int, s: String)
          |
          |object Test {
          |  Foo(42,
          |    "boo",
          |  )
          |}""".stripMargin
    )

  def testCaseClassCreationTrailingComma2(): Unit =
    doTestWithCallArgsSettings(newLineAfterLParen = NO_NEW_LINE, newLineBeforeRParen = false)(
      singleLineText =
        """case class Foo(i: Int, s: String)
          |
          |object Test {
          |  Foo(42, "boo",)
          |}""".stripMargin,
      multiLineText =
        """case class Foo(i: Int, s: String)
          |
          |object Test {
          |  Foo(42,
          |    "boo",)
          |}""".stripMargin
    )
}
