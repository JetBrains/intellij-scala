package org.jetbrains.plugins.scala
package lang
package completeStatement

import com.intellij.testFramework.EditorTestUtil

/**
  * @author Ksenia.Sautina
  * @since 2/25/13
  */
class ScalaCompleteFormatTest extends ScalaCompleteStatementTestBase {

  import EditorTestUtil.{CARET_TAG => CARET}

  def testFormat(): Unit = doCompletionTest(
    fileText =
      s"""
         |class B {
         |  val d=7+7+7+77$CARET
         |}
      """.stripMargin,
    resultText =
      s"""
         |class B {
         |  val d = 7 + 7 + 7 + 77$CARET
         |}
      """.stripMargin
  )

  def testFormat2(): Unit = doCompletionTest(
    fileText =
      s"""
         |class B {
         |  if (true) {
         |    val d=7+7+7+7+7
         |    val dd =6+6+6+6+6$CARET
         |  }
         |}
      """.stripMargin,
    resultText =
      s"""
         |class B {
         |  if (true) {
         |    val d=7+7+7+7+7
         |    val dd = 6 + 6 + 6 + 6 + 6$CARET
         |  }
         |}
      """.stripMargin
  )

  def testFormat3(): Unit = doCompletionTest(
    fileText =
      s"""
         |class B {
         |  if (true) {
         |    val d=7+7+7+7+7
         |    val dd =6+6+6+6+6$CARET
         |    val ddd =6+6+6+6+6
         |  }
         |}
      """.stripMargin,
    resultText =
      s"""
         |class B {
         |  if (true) {
         |    val d=7+7+7+7+7
         |    val dd = 6 + 6 + 6 + 6 + 6$CARET
         |    val ddd =6+6+6+6+6
         |  }
         |}
      """.stripMargin
  )
}
