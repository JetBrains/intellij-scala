package org.jetbrains.plugins.scala
package codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionTestBase

/**
  * Nikolay.Tropin
  * 5/21/13
  */
abstract class OperationsOnCollectionInspectionTest extends ScalaInspectionTestBase {
  override protected val classOfInspection: Class[_ <: OperationOnCollectionInspection]

  protected val hint: String

  override protected lazy val description: String = hint

  protected def doTest(selected: String, text: String, result: String): Unit = {
    checkTextHasError(selected)
    testQuickFix(text, result, hint)
  }
}