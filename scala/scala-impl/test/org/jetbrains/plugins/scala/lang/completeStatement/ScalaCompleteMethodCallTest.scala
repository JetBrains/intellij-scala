package org.jetbrains.plugins.scala
package lang
package completeStatement

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
