package org.jetbrains.plugins.scala
package codeInspection

import com.intellij.codeInspection.LocalInspectionTool
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.jetbrains.plugins.scala.codeInspection.booleans.DoubleNegationInspection

/**
 * Nikolay.Tropin
 * 6/3/13
 */
abstract class ScalaLightInspectionFixtureTestAdapter extends ScalaLightCodeInsightFixtureTestAdapter{
  protected val START = ScalaLightCodeInsightFixtureTestAdapter.SELECTION_START
  protected val END = ScalaLightCodeInsightFixtureTestAdapter.SELECTION_END
  protected def classOfInspection: Class[_ <: LocalInspectionTool]
  protected def annotation: String
  protected def normalize(str: String): String = str.stripMargin.replace("\r", "").trim

  protected def check(text: String): Unit = {
    checkTextHasError(normalize(text), normalize(annotation), classOfInspection)
  }

  protected def testFix(text: String, result: String, hint: String): Unit = {
    testQuickFix(normalize(text), normalize(result), hint, classOf[DoubleNegationInspection])
  }

  override protected def checkTextHasNoErrors(text: String): Unit = {
    checkTextHasNoErrors(normalize(text), annotation, classOfInspection)
  }

  protected def checkTextHasError(text: String) {
    checkTextHasError(normalize(text), annotation, classOfInspection)
  }
}
