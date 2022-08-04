package org.jetbrains.plugins.scala.codeInsight.intention.lists

abstract class ScalaSplitJoinTupleTypesIntentionTestBase extends ScalaSplitJoinLineIntentionTestBase {
  private def doTest(singleLineText: String, multiLineText: String): Unit =
    doTest(singleLineText, multiLineText, listStartChar = '(')

  def testSimpleType(): Unit =
    doTest(
      singleLineText = """def foo: (Int, String, Boolean) = ???""",
      multiLineText =
        """def foo: (
          |  Int,
          |    String,
          |    Boolean
          |  ) = ???""".stripMargin
    )

  def testSimpleTypeTrailingComma(): Unit =
    doTest(
      singleLineText = """def foo: (Int, String, Boolean, ) = ???""",
      multiLineText =
        """def foo: (
          |  Int,
          |    String,
          |    Boolean,
          |  ) = ???""".stripMargin
    )

  def testSimpleTypeWithOneElement(): Unit =
    checkIntentionIsNotAvailable(s"def foo: $CARET(Int) = ???")

  def testSimpleTypeWithOneElementTrailingComma(): Unit =
    checkIntentionIsNotAvailable(s"def foo: $CARET(Int, ) = ???")

  def testTypeArgumentFirst(): Unit =
    doTest(
      singleLineText =
        """def foo[A, B, C] = ???
          |
          |object Test {
          |  foo[(String, Boolean, CharSequence), Int, Long]
          |}""".stripMargin,
      multiLineText =
        """def foo[A, B, C] = ???
          |
          |object Test {
          |  foo[(
          |    String,
          |      Boolean,
          |      CharSequence
          |    ), Int, Long]
          |}""".stripMargin
    )

  def testTypeArgumentInTheMiddle(): Unit =
    doTest(
      singleLineText =
        """def foo[A, B, C] = ???
          |
          |object Test {
          |  foo[Int, (String, Boolean, CharSequence), Long]
          |}""".stripMargin,
      multiLineText =
        """def foo[A, B, C] = ???
          |
          |object Test {
          |  foo[Int, (
          |    String,
          |      Boolean,
          |      CharSequence
          |    ), Long]
          |}""".stripMargin
    )

  def testTypeArgumentLast(): Unit =
    doTest(
      singleLineText =
        """def foo[A, B, C] = ???
          |
          |object Test {
          |  foo[Int, Long, (String, Boolean, CharSequence)]
          |}""".stripMargin,
      multiLineText =
        """def foo[A, B, C] = ???
          |
          |object Test {
          |  foo[Int, Long, (
          |    String,
          |      Boolean,
          |      CharSequence
          |    )]
          |}""".stripMargin
    )

  def testTypeArgumentWithOneElement(): Unit =
    checkIntentionIsNotAvailable(
      s"""def foo[A] = ???
         |
         |object Test {
         |  foo[$CARET(Int)]
         |}""".stripMargin
    )

  def testTypeArgumentWithOneElementTrailingComma(): Unit =
    checkIntentionIsNotAvailable(
      s"""def foo[A] = ???
         |
         |object Test {
         |  foo[$CARET(Int, )]
         |}""".stripMargin
    )
}
