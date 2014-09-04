package org.jetbrains.plugins.scala
package lang.completeStatement

import org.jetbrains.plugins.scala.codeInsight.ScalaCodeInsightTestBase

/**
 * @author Ksenia.Sautina
 * @since 2/25/13
 */
class ScalaCompleteFormatTest extends ScalaCodeInsightTestBase {
  def testFormat() {
    val fileText =
      """
        |class B {
        |  val d=7+7+7+77<caret>
        |}
      """.stripMargin('|').replaceAll("\r", "").trim()
    val resultText =
      """
        |class B {
        |  val d = 7 + 7 + 7 + 77<caret>
        |}
      """.stripMargin('|').replaceAll("\r", "").trim()

    configureFromFileTextAdapter("dummy.scala", fileText)
    invokeSmartEnter()
    checkResultByText(resultText)
  }

  def testFormat2() {
    val fileText =
      """
        |class B {
        |  if (true) {
        |    val d=7+7+7+7+7
        |    val dd =6+6+6+6+6<caret>
        |  }
        |}
      """.stripMargin('|').replaceAll("\r", "").trim()
    val resultText =
      """
        |class B {
        |  if (true) {
        |    val d=7+7+7+7+7
        |    val dd = 6 + 6 + 6 + 6 + 6<caret>
        |  }
        |}
      """.stripMargin('|').replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)
    invokeSmartEnter()
    checkResultByText(resultText)
  }

  def testFormat3() {
    val fileText =
      """
        |class B {
        |  if (true) {
        |    val d=7+7+7+7+7
        |    val dd =6+6+6+6+6<caret>
        |    val ddd =6+6+6+6+6
        |  }
        |}
      """.stripMargin('|').replaceAll("\r", "").trim()
    val resultText =
      """
        |class B {
        |  if (true) {
        |    val d=7+7+7+7+7
        |    val dd = 6 + 6 + 6 + 6 + 6<caret>
        |    val ddd =6+6+6+6+6
        |  }
        |}
      """.stripMargin('|').replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)
    invokeSmartEnter()
    checkResultByText(resultText)
  }

  def testFormatJava() {
    val fileText =
      """
        |class B {
        |    int d=7+7+7+77;<caret>
        |}
      """.stripMargin('|').replaceAll("\r", "").trim()
    val resultText =
      """
        |class B {
        |    int d = 7 + 7 + 7 + 77;<caret>
        |}
      """.stripMargin('|').replaceAll("\r", "").trim()

    configureFromFileTextAdapter("dummy.java", fileText)
    invokeSmartEnter()
    checkResultByText(resultText)
  }


}
