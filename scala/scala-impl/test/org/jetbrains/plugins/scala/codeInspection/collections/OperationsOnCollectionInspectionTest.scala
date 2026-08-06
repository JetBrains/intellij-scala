package org.jetbrains.plugins.scala
package codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionTestBase

abstract class OperationsOnCollectionInspectionTest extends ScalaInspectionTestBase {
  override protected val classOfInspection: Class[_ <: OperationOnCollectionInspection]

  protected val hint: String

  override protected lazy val description: String = hint

  protected def doTest(selected: String, text: String, result: String): Unit = {
    checkTextHasError(selected)
    testQuickFix(text, result, hint)
  }
}