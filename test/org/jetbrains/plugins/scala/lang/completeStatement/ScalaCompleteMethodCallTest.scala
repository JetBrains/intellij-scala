package org.jetbrains.plugins.scala
package lang
package completeStatement

import com.intellij.testFramework.EditorTestUtil

/**
  * @author Ksenia.Sautina
  * @since 1/28/13
  */
class ScalaCompleteMethodCallTest extends ScalaCompleteStatementTestBase {

  import EditorTestUtil.{CARET_TAG => CARET}

  def testMethodCall(): Unit = doCompletionTest(
    fileText =
      s"""
         |class B {
         |  def method() {}
         |
         |  method($CARET
         |}
      """.stripMargin,
    resultText =
      s"""
         |class B {
         |  def method() {}
         |
         |  method()$CARET
         |}
      """.stripMargin
  )
}
