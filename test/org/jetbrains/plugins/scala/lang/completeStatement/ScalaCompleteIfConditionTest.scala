package org.jetbrains.plugins.scala
package lang.completeStatement

import org.jetbrains.plugins.scala.codeInsight.ScalaCodeInsightTestBase

/**
 * @author Ksenia.Sautina
 * @since 2/25/13
 */
class ScalaCompleteIfConditionTest extends ScalaCodeInsightTestBase {
  def testIfCondition() {
    val fileText =
      """
        |class B {
        |  def method() {
        |    if <caret>
        |  }
        |}
      """.stripMargin('|').replaceAll("\r", "").trim()
    val resultText =
      """
        |class B {
        |  def method() {
        |    if (<caret>) {
        |    }
        |  }
        |}
      """.stripMargin('|').replaceAll("\r", "").trim()

    configureFromFileTextAdapter("dummy.scala", fileText)
    invokeSmartEnter()
    checkResultByText(resultText)
  }

  def testIfCondition2() {
    val fileText =
      """
        |class B {
        |  def method() {
        |    if<caret>
        |  }
        |}
      """.stripMargin('|').replaceAll("\r", "").trim()
    val resultText =
      """
        |class B {
        |  def method() {
        |    if (<caret>) {
        |    }
        |  }
        |}
      """.stripMargin('|').replaceAll("\r", "").trim()

    configureFromFileTextAdapter("dummy.scala", fileText)
    invokeSmartEnter()
    checkResultByText(resultText)
  }

  def testIfCondition3() {
    val fileText =
      """
        |class B {
        |  def method() {
        |    if (<caret>
        |  }
        |}
      """.stripMargin('|').replaceAll("\r", "").trim()
    val resultText =
      """
        |class B {
        |  def method() {
        |    if (<caret>) {
        |    }
        |  }
        |}
      """.stripMargin('|').replaceAll("\r", "").trim()

    configureFromFileTextAdapter("dummy.scala", fileText)
    invokeSmartEnter()
    checkResultByText(resultText)
  }

  def testIfCondition4() {
    val fileText =
      """
        |class B {
        |  def method() {
        |    if (true<caret>) {
        |    }
        |  }
        |}
      """.stripMargin('|').replaceAll("\r", "").trim()
    val resultText =
      """
        |class B {
        |  def method() {
        |    if (true) {
        |      <caret>
        |    }
        |  }
        |}
      """.stripMargin('|').replaceAll("\r", "").trim()

    configureFromFileTextAdapter("dummy.scala", fileText)
    invokeSmartEnter()
    checkResultByText(resultText)
  }

  def testIfCondition5() {
    val fileText =
      """
        |class B {
        |  def method() {
        |    if (true) {<caret>
        |  }
        |}
      """.stripMargin('|').replaceAll("\r", "").trim()
    val resultText =
      """
        |class B {
        |  def method() {
        |    if (true) {
        |      <caret>
        |    }
        |  }
        |}
      """.stripMargin('|').replaceAll("\r", "").trim()

    configureFromFileTextAdapter("dummy.scala", fileText)
    invokeSmartEnter()
    checkResultByText(resultText)
  }

  def testIfCondition6() {
    val fileText =
      """
        |class B {
        |  def method() {
        |    if (true<caret>) {
        |      println()
        |    }
        |  }
        |}
      """.stripMargin('|').replaceAll("\r", "").trim()
    val resultText =
      """
        |class B {
        |  def method() {
        |    if (true) {
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

  def testIfCondition7() {
    val fileText =
      """
        |class B {
        |  def method() {
        |    if ()<caret>
        |  }
        |}
      """.stripMargin('|').replaceAll("\r", "").trim()
    val resultText =
      """
        |class B {
        |  def method() {
        |    if (<caret>) {
        |
        |    }
        |  }
        |}
      """.stripMargin('|').replaceAll("\r", "").trim()

    configureFromFileTextAdapter("dummy.scala", fileText)
    invokeSmartEnter()
    checkResultByText(resultText)
  }

  def testIfConditionJava() {
    val fileText =
      """
        |class B {
        |    public static void main(String[] args) {
        |        if <caret>
        |    }
        |}
      """.stripMargin('|').replaceAll("\r", "").trim()
    val resultText =
      """
        |class B {
        |    public static void main(String[] args) {
        |        if (<caret>) {
        |        }
        |    }
        |}
      """.stripMargin('|').replaceAll("\r", "").trim()

    configureFromFileTextAdapter("dummy.java", fileText)
    invokeSmartEnter()
    checkResultByText(resultText)
  }

  def testIfCondition2Java() {
    val fileText =
      """
        |class B {
        |    public static void main(String[] args) {
        |        if ()<caret>
        |    }
        |}
      """.stripMargin('|').replaceAll("\r", "").trim()
    val resultText =
      """
        |class B {
        |    public static void main(String[] args) {
        |        if (<caret>) {
        |        }
        |    }
        |}
      """.stripMargin('|').replaceAll("\r", "").trim()

    configureFromFileTextAdapter("dummy.java", fileText)
    invokeSmartEnter()
    checkResultByText(resultText)
  }
}
