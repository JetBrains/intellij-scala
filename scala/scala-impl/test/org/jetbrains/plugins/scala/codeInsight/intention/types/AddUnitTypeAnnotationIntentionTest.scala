package org.jetbrains.plugins.scala
package codeInsight
package intention
package types

import org.jetbrains.plugins.scala.codeInsight.intentions.ScalaIntentionTestBase

class AddUnitTypeAnnotationIntentionTest extends ScalaIntentionTestBase {

  override def familyName: String = AddUnitTypeAnnotationIntention.FamilyName

  def test(): Unit = doTest(
    text =
      s"""class NameParameters {
         |  def doSomethin${CARET}g(flag: Boolean) {}
         |}""".stripMargin,
    resultText =
      s"""class NameParameters {
         |  def doSomethin${CARET}g(flag: Boolean): Unit = {}
         |}""".stripMargin
  )
}
