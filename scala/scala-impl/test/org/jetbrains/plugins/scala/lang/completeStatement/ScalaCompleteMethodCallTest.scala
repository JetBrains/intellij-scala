package org.jetbrains.plugins.scala
package lang
package completeStatement

/**
  * @author Ksenia.Sautina
  * @since 1/28/13
  */
class ScalaCompleteMethodCallTest extends ScalaCompleteStatementTestBase {

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
