package org.jetbrains.plugins.scala
package lang.completeStatement

/**
 * @author Ksenia.Sautina
 * @author Dmitry.Naydanov
 * @since 2/25/13
 */
class ScalaCompleteWhileConditionTest extends ScalaCompleteStatementTestBase {
  def testWhileCondition1() {
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
        |
        |    }
        |  }
        |}
      """.stripMargin('|').replaceAll("\r", "").trim()

    checkScalaFileByText(fileText, resultText)
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
        |
        |    }
        |  }
        |}
      """.stripMargin('|').replaceAll("\r", "").trim()

    checkScalaFileByText(fileText, resultText)
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
        |
        |    }
        |  }
        |}
      """.stripMargin('|').replaceAll("\r", "").trim()

    checkScalaFileByText(fileText, resultText)
  }


  def testWhileCondition4() {
    val fileText =
      """
        |class B {
        |  def method() {
        |    while (true<caret>) {
        |
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

    checkScalaFileByText(fileText, resultText)
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

    checkScalaFileByText(fileText, resultText)
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

    checkScalaFileByText(fileText, resultText)
  }

  def testWhileCondition7() {
    val fileText =
      """
        |class B {
        |  def method() {
        |    while ()<caret>
        |  }
        |}
      """.stripMargin
    val resultText =
      """
        |class B {
        |  def method() {
        |    while (<caret>) {
        |
        |    }
        |  }
        |}
      """.stripMargin

    checkScalaFileByText(fileText, resultText)
  }

  def testWhileCondition8() {
    val fileText =
      """
        |object A {
        |  while (true)<caret>
        |}
      """.stripMargin

    val resultText =
      """
        |object A {
        |  while (true) {
        |    <caret>
        |  }
        |}
      """.stripMargin

    checkScalaFileByText(fileText, resultText)
  }
}
