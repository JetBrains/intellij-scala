package org.jetbrains.plugins.scala.annotator.quickfix

import org.jetbrains.plugins.scala.annotator.quickfix.ChangeTypeFixTest.DescriptionRegex
import org.jetbrains.plugins.scala.codeInspection.ScalaAnnotatorQuickFixTestBase

/**
 * NOTE: I test [[org.jetbrains.plugins.scala.annotator.quickfix.ChangeTypeFix]] indirectly via it's usage in annotator
 */
class ChangeTypeFixTest extends ScalaAnnotatorQuickFixTestBase {
  override protected def description: String = ???

  override protected def descriptionMatches(s: String): Boolean = DescriptionRegex.matches(s)

  def testSimple(): Unit = {
    testQuickFix(
      """object wrapper {
        |  var value: Int = (??? : String)
        |}
        |""".stripMargin,
      s"""object wrapper {
         |  var value: String = (??? : String)
         |}
         |""".stripMargin,
      "Change type 'Int' to 'String'"
    )
  }

  def testWidenNewType(): Unit = {
    testQuickFix(
      """object wrapper {
        |  var value: Int = "my text"
        |}
        |""".stripMargin,
      s"""object wrapper {
         |  var value: String = "my text"
         |}
         |""".stripMargin,
      "Change type 'Int' to 'String'"
    )
  }
}

object ChangeTypeFixTest {
  private val DescriptionRegex = "Expression of type .*? doesn't conform to expected type .*?".r
}