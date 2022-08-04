package org.jetbrains.plugins.scala.codeInsight.intention.lists

abstract class ScalaSplitJoinTuplesIntentionTestBase extends ScalaSplitJoinLineIntentionTestBase {
  private def doTest(singleLineText: String, multiLineText: String): Unit =
    doTest(singleLineText, multiLineText, listStartChar = '(')

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

  def testTupleWithOneArg(): Unit =
    checkIntentionIsNotAvailable(
      s"""object Test {
         |  $CARET("boo")
         |}""".stripMargin
    )

  def testTupleWithOneArgTrailingComma(): Unit =
    checkIntentionIsNotAvailable(
      s"""object Test {
         |  $CARET("boo", )
         |}""".stripMargin
    )
}
