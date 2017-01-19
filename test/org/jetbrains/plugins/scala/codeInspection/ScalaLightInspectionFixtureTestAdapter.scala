package org.jetbrains.plugins.scala
package codeInspection

import com.intellij.codeInspection.LocalInspectionTool
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter

/**
 * Nikolay.Tropin
 * 6/3/13
 */
abstract class ScalaLightInspectionFixtureTestAdapter extends ScalaLightCodeInsightFixtureTestAdapter {

  import ScalaLightCodeInsightFixtureTestAdapter._

  protected def classOfInspection: Class[_ <: LocalInspectionTool]
  protected def annotation: String

  protected def check(text: String): Unit = {
    checkTextHasError(normalize(text), normalize(annotation), classOfInspection)
  }

  protected def testFix(text: String, result: String, hint: String): Unit = {
    testQuickFix(normalize(text), normalize(result), hint, classOfInspection)
  }

  override protected def checkTextHasNoErrors(text: String): Unit = {
    checkTextHasNoErrors(normalize(text), annotation, classOfInspection)
  }

  protected def checkTextHasError(text: String) {
    checkTextHasError(normalize(text), annotation, classOfInspection)
  }
}
