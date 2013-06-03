package org.jetbrains.plugins.scala
package codeInspection

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import com.intellij.codeInsight.CodeInsightTestCase
import com.intellij.codeInspection.LocalInspectionTool
import org.jetbrains.plugins.scala.codeInspection.booleans.DoubleNegationInspection

/**
 * Nikolay.Tropin
 * 6/3/13
 */
abstract class ScalaLightInspectionFixtureTestAdapter extends ScalaLightCodeInsightFixtureTestAdapter{
  protected val START = CodeInsightTestCase.SELECTION_START_MARKER
  protected val END = CodeInsightTestCase.SELECTION_END_MARKER
  protected def classOfInspection: Class[_ <: LocalInspectionTool]
  protected def annotation: String

  protected def check(text: String) {
    checkTextHasError(text, annotation, classOfInspection)
  }

  protected def testFix(text: String, result: String, hint: String) {
    testQuickFix(text.replace("\r", ""), result.replace("\r", ""), hint, classOf[DoubleNegationInspection])
  }

  protected def checkHasNoErrors(text: String) {
    checkTextHasNoErrors(text, annotation, classOfInspection)
  }
}
