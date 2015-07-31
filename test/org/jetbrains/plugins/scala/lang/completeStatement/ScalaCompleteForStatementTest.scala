package org.jetbrains.plugins.scala
package lang.completeStatement

/**
 * @author Ksenia.Sautina
 * @since 2/25/13
 */
class ScalaCompleteForStatementTest extends ScalaCompleteStatementTestBase {

  def testForStatement1() {
    val fileText =
      """
        |class B {
        |  def method() {
        |    for <caret>
        |  }
        |}
      """.stripMargin('|').replaceAll("\r", "").trim()
    val resultText =
      """
        |class B {
        |  def method() {
        |    for (<caret>) {
        |    }
        |  }
        |}
      """.stripMargin('|').replaceAll("\r", "").trim()

    checkScalaFileByText(fileText, resultText)
  }

  def testForStatement2() {
    val fileText =
      """
        |class B {
        |  def method() {
        |    for<caret>
        |  }
        |}
      """.stripMargin('|').replaceAll("\r", "").trim()
    val resultText =
      """
        |class B {
        |  def method() {
        |    for (<caret>) {
        |    }
        |  }
        |}
      """.stripMargin('|').replaceAll("\r", "").trim()

    checkScalaFileByText(fileText, resultText)
  }

  def testForStatement3() {
    val fileText =
      """
        |class B {
        |  def method() {
        |    for (<caret>
        |  }
        |}
      """.stripMargin('|').replaceAll("\r", "").trim()
    val resultText =
      """
        |class B {
        |  def method() {
        |    for (<caret>) {
        |
        |    }
        |  }
        |}
      """.stripMargin('|').replaceAll("\r", "").trim()

    checkScalaFileByText(fileText, resultText)
  }

  def testForStatement4() {
    val fileText =
      """
        |class B {
        |  def method() {
        |    for (i <- 1 to 10<caret>) {
        |    }
        |  }
        |}
      """.stripMargin('|').replaceAll("\r", "").trim()
    val resultText =
      """
        |class B {
        |  def method() {
        |    for (i <- 1 to 10) {
        |      <caret>
        |    }
        |  }
        |}
      """.stripMargin('|').replaceAll("\r", "").trim()

    checkScalaFileByText(fileText, resultText)
  }

  def testForStatement5() {
    val fileText =
      """
        |class B {
        |  def method() {
        |    for (i <- 1 to 10) {<caret>
        |  }
        |}
      """.stripMargin('|').replaceAll("\r", "").trim()
    val resultText =
      """
        |class B {
        |  def method() {
        |    for (i <- 1 to 10) {
        |      <caret>
        |    }
        |  }
        |}
      """.stripMargin('|').replaceAll("\r", "").trim()

    checkScalaFileByText(fileText, resultText)
  }

  def testForStatement6() {
    val fileText =
      """
        |class B {
        |  def method() {
        |    for (i <- 1 to 10<caret>) {
        |      println()
        |    }
        |  }
        |}
      """.stripMargin('|').replaceAll("\r", "").trim()
    val resultText =
      """
        |class B {
        |  def method() {
        |    for (i <- 1 to 10) {
        |      <caret>
        |      println()
        |    }
        |  }
        |}
      """.stripMargin('|').replaceAll("\r", "").trim()

    checkScalaFileByText(fileText, resultText)
  }

  def testForStatement7() {
    val fileText =
      """
        |class B {
        |  def method() {
        |    for ()<caret>
        |  }
        |}
      """.stripMargin('|').replaceAll("\r", "").trim()
    val resultText =
      """
        |class B {
        |  def method() {
        |    for (<caret>) {
        |
        |    }
        |  }
        |}
      """.stripMargin('|').replaceAll("\r", "").trim()

    checkScalaFileByText(fileText, resultText)
  }


}
