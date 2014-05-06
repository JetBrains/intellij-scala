package org.jetbrains.plugins.scala
package codeInspection.collections
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import com.intellij.codeInsight.CodeInsightTestCase
import org.jetbrains.plugins.scala.codeInspection.InspectionBundle

/**
 * Nikolay.Tropin
 * 5/21/13
 */
abstract class OperationsOnCollectionInspectionTest extends ScalaLightCodeInsightFixtureTestAdapter {
  val START = ScalaLightCodeInsightFixtureTestAdapter.SELECTION_START
  val END = ScalaLightCodeInsightFixtureTestAdapter.SELECTION_END
  val annotation = InspectionBundle.message("operation.on.collection.name")
  val inspectionClass: Class[_ <: OperationOnCollectionInspection]
  def hint: String

  protected def check(text: String) {
    checkTextHasError(text, annotation, inspectionClass)
  }

  protected def testFix(text: String, result: String, hint: String) {
    testQuickFix(text.replace("\r", ""), result.replace("\r", ""), hint, inspectionClass)
  }
}