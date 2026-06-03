package org.jetbrains.plugins.scala.codeInsight.intention.lists

import org.junit.Test

abstract class ScalaSplitJoinTuplesIntentionTestBase extends ScalaSplitJoinLineIntentionTestBase {
  private def doTest(singleLineText: String, multiLineText: String): Unit =
    doTest(singleLineText, multiLineText, listStartChar = '(')

  @Test
  def testTuple(): Unit =
    doTest(
      singleLineText =
        """object Test {
          |  (1, "foo", true)
          |}""".stripMargin,
      multiLineText =
        """object Test {
          |  (
          |    1,
          |    "foo",
          |    true
          |  )
          |}""".stripMargin
    )

  @Test
  def testTupleTrailingComma(): Unit =
    doTest(
      singleLineText =
        """object Test {
          |  (1, "foo", true, )
          |}""".stripMargin,
      multiLineText =
        """object Test {
          |  (
          |    1,
          |    "foo",
          |    true,
          |  )
          |}""".stripMargin
    )

  @Test
  def testTupleWithOneArg(): Unit =
    checkIntentionIsNotAvailable(
      s"""object Test {
         |  $CARET("boo")
         |}""".stripMargin
    )

  @Test
  def testTupleWithOneArgTrailingComma(): Unit =
    checkIntentionIsNotAvailable(
      s"""object Test {
         |  $CARET("boo", )
         |}""".stripMargin
    )
}
