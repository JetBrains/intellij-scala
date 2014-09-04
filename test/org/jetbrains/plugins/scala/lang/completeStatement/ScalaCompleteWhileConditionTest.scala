package org.jetbrains.plugins.scala
package lang.completeStatement

import org.jetbrains.plugins.scala.codeInsight.ScalaCodeInsightTestBase

/**
 * @author Ksenia.Sautina
 * @since 2/25/13
 */
class ScalaCompleteWhileConditionTest extends ScalaCodeInsightTestBase {

  def testWhileCondition() {
    val fileText =
      """
        |class B {
        |  def method() {
        |    while <caret>
        |  }
        |}
      """.stripMargin('|').replaceAll("\r", "").trim()
    val resultText =
      """
        |class B {
        |  def method() {
        |    while (<caret>) {
        |    }
        |  }
        |}
      """.stripMargin('|').replaceAll("\r", "").trim()

    configureFromFileTextAdapter("dummy.scala", fileText)
    invokeSmartEnter()
    checkResultByText(resultText)
  }

  def testWhileCondition2() {
    val fileText =
      """
        |class B {
        |  def method() {
        |    while<caret>
        |  }
        |}
      """.stripMargin('|').replaceAll("\r", "").trim()
    val resultText =
      """
        |class B {
        |  def method() {
        |    while (<caret>) {
        |    }
        |  }
        |}
      """.stripMargin('|').replaceAll("\r", "").trim()

    configureFromFileTextAdapter("dummy.scala", fileText)
    invokeSmartEnter()
    checkResultByText(resultText)
  }

  def testWhileCondition3() {
    val fileText =
      """
        |class B {
        |  def method() {
        |    while (<caret>
        |  }
        |}
      """.stripMargin('|').replaceAll("\r", "").trim()
    val resultText =
      """
        |class B {
        |  def method() {
        |    while (<caret>) {
        |    }
        |  }
        |}
      """.stripMargin('|').replaceAll("\r", "").trim()

    configureFromFileTextAdapter("dummy.scala", fileText)
    invokeSmartEnter()
    checkResultByText(resultText)
  }


  def testWhileCondition4() {
    val fileText =
      """
        |class B {
        |  def method() {
        |    while (true<caret>) {
        |    }
        |  }
        |}
      """.stripMargin('|').replaceAll("\r", "").trim()
    val resultText =
      """
        |class B {
        |  def method() {
        |    while (true) {
        |      <caret>
        |    }
        |  }
        |}
      """.stripMargin('|').replaceAll("\r", "").trim()

    configureFromFileTextAdapter("dummy.scala", fileText)
    invokeSmartEnter()
    checkResultByText(resultText)
  }

  def testWhileCondition5() {
    val fileText =
      """
        |class B {
        |  def method() {
        |    while (true) {<caret>
        |  }
        |}
      """.stripMargin('|').replaceAll("\r", "").trim()
    val resultText =
      """
        |class B {
        |  def method() {
        |    while (true) {
        |      <caret>
        |    }
        |  }
        |}
      """.stripMargin('|').replaceAll("\r", "").trim()

    configureFromFileTextAdapter("dummy.scala", fileText)
    invokeSmartEnter()
    checkResultByText(resultText)
  }

  def testWhileCondition6() {
    val fileText =
      """
        |class B {
        |  def method() {
        |    while (true<caret>) {
        |      println()
        |    }
        |  }
        |}
      """.stripMargin('|').replaceAll("\r", "").trim()
    val resultText =
      """
        |class B {
        |  def method() {
        |    while (true) {
        |      <caret>
        |      println()
        |    }
        |  }
        |}
      """.stripMargin('|').replaceAll("\r", "").trim()

    configureFromFileTextAdapter("dummy.scala", fileText)
    invokeSmartEnter()
    checkResultByText(resultText)
  }

  def testWhileCondition7() {
    val fileText =
      """
        |class B {
        |  def method() {
        |    while ()<caret>
        |  }
        |}
      """.stripMargin('|').replaceAll("\r", "").trim()
    val resultText =
      """
        |class B {
        |  def method() {
        |    while (<caret>) {
        |
        |    }
        |  }
        |}
      """.stripMargin('|').replaceAll("\r", "").trim()

    configureFromFileTextAdapter("dummy.scala", fileText)
    invokeSmartEnter()
    checkResultByText(resultText)
  }
}
