package org.jetbrains.plugins.scala
package lang.completeStatement

/**
 * @author Ksenia.Sautina
 * @since 2/25/13
 */
class ScalaCompleteIfConditionTest extends ScalaCompleteStatementTestBase {
  def testIfCondition1() {
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
        |
        |    }
        |  }
        |}
      """.stripMargin('|').replaceAll("\r", "").trim()

    checkScalaFileByText(fileText, resultText)
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
        |
        |    }
        |  }
        |}
      """.stripMargin('|').replaceAll("\r", "").trim()

    checkScalaFileByText(fileText, resultText)
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
        |
        |    }
        |  }
        |}
      """.stripMargin('|').replaceAll("\r", "").trim()

    checkScalaFileByText(fileText, resultText)
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

    checkScalaFileByText(fileText, resultText)
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

    checkScalaFileByText(fileText, resultText)
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

    checkScalaFileByText(fileText, resultText)
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

    checkScalaFileByText(fileText, resultText)
  }

  def testIfConditionJava() { //WHAT THE _?!
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

    checkJavaFileByText(fileText, resultText)
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

    checkJavaFileByText(fileText, resultText)
  }
}
