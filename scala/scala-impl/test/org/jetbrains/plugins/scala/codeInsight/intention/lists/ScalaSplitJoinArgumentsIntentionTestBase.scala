package org.jetbrains.plugins.scala.codeInsight.intention.lists

import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings.{NEW_LINE_ALWAYS, NEW_LINE_FOR_MULTIPLE_ARGUMENTS, NO_NEW_LINE}
import org.junit.Test

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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
  def testMethodCallWithOneArg(): Unit =
    checkIntentionIsNotAvailable(
      s"""def foo(i: Int): Unit = {}
         |
         |object Test {
         |  foo$CARET(42)
         |}""".stripMargin
    )

  @Test
  def testMethodCallWithoutArgs(): Unit =
    checkIntentionIsNotAvailable(
      s"""def foo(): Unit = {}
         |
         |object Test {
         |  foo$CARET()
         |}""".stripMargin
    )

  // Class creation

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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
