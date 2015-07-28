package org.jetbrains.plugins.scala
package lang.completeStatement

/**
 * @author Ksenia.Sautina
 * @since 2/25/13
 */
class ScalaCompleteFormatTest extends ScalaCompleteStatementTestBase {
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

    checkScalaFileByText(fileText, resultText)
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

    checkScalaFileByText(fileText, resultText)
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

    checkScalaFileByText(fileText, resultText)
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

    checkJavaFileByText(fileText, resultText)
  }
}
