package org.jetbrains.plugins.scala
package lang.completeStatement

/**
 * @author Ksenia.Sautina
 * @since 1/28/13
 */
class ScalaCompleteMethodCallTest extends ScalaCompleteStatementTestBase {
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

    checkScalaFileByText(fileText, resultText)
  }
}
