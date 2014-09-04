package org.jetbrains.plugins.scala
package lang.completeStatement

import org.jetbrains.plugins.scala.codeInsight.ScalaCodeInsightTestBase

/**
 * @author Ksenia.Sautina
 * @since 1/28/13
 */
class ScalaCompleteMethodCallTest extends ScalaCodeInsightTestBase {

  def testMethodCall() {
    val fileText =
      """
        |class B {
        |  def method() {}
        |
        |  method(<caret>
        |}
      """.stripMargin('|').replaceAll("\r", "").trim()
    val resultText =
      """
        |class B {
        |  def method() {}
        |
        |  method()<caret>
        |}
      """.stripMargin('|').replaceAll("\r", "").trim()

    configureFromFileTextAdapter("dummy.scala", fileText)
    invokeSmartEnter()
    checkResultByText(resultText)
  }
}
