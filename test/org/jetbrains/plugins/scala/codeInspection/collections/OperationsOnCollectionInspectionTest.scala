package org.jetbrains.plugins.scala
package codeInspection.collections
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter

/**
 * Nikolay.Tropin
 * 5/21/13
 */
abstract class OperationsOnCollectionInspectionTest extends ScalaLightCodeInsightFixtureTestAdapter {
  val START = ScalaLightCodeInsightFixtureTestAdapter.SELECTION_START
  val END = ScalaLightCodeInsightFixtureTestAdapter.SELECTION_END
  val inspectionClass: Class[_ <: OperationOnCollectionInspection]
  def hint: String
  def description: String = hint

  protected def check(text: String, description: String = description) {
    checkTextHasError(text, description, inspectionClass)
  }

  protected def testFix(text: String, result: String, hint: String) {
    testQuickFix(text.replace("\r", ""), result.replace("\r", ""), hint, inspectionClass)
  }

  def doTest(selected: String, text: String, result: String) = {
    check(selected)
    testFix(text, result, hint)
  }

}