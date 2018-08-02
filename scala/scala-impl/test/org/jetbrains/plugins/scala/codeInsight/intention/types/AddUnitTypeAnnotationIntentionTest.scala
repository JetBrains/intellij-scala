package org.jetbrains.plugins.scala
package codeInsight
package intention
package types

import com.intellij.testFramework.EditorTestUtil
import org.jetbrains.plugins.scala.codeInsight.intentions.ScalaIntentionTestBase

/**
  * @author Alefas
  * @since 29/08/16
  */
class AddUnitTypeAnnotationIntentionTest extends ScalaIntentionTestBase {

  import EditorTestUtil.{CARET_TAG => CARET}

  override def familyName: String = AddUnitTypeAnnotationIntention.FamilyName

  def test(): Unit = doTest(
    text =
      s"""class NameParameters {
         |  def doSomethin${CARET}g(flag: Boolean) {}
         |}""",
    resultText =
      s"""class NameParameters {
         |  def doSomethin${CARET}g(flag: Boolean): Unit = {}
         |}"""
  )
}
