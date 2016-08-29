package org.jetbrains.plugins.scala.codeInsight.intentions.types

import org.jetbrains.plugins.scala.codeInsight.intention.types.AddUnitTypeAnnotationIntention
import org.jetbrains.plugins.scala.codeInsight.intentions.ScalaIntentionTestBase

/**
  * @author Alefas
  * @since 29/08/16
  */
class AddUnitTypeAnnotationIntentionTest extends ScalaIntentionTestBase {
  override def familyName: String = AddUnitTypeAnnotationIntention.familyName

  def test() {
    val text =
      """
        |class NameParameters {
        |  def doSomethin<caret>g(flag: Boolean) {}
        |}
      """
    val resultText =
      """
        |class NameParameters {
        |  def doSomethin<caret>g(flag: Boolean): Unit = {}
        |}
      """

    doTest(text, resultText)
  }
}
