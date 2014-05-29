package org.jetbrains.plugins.scala
package codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.InspectionBundle

/**
 * Nikolay.Tropin
 * 2014-05-07
 */
class NotIsEmptyTest extends OperationsOnCollectionInspectionTest {
  val hint = InspectionBundle.message("not.isEmpty.hint")
  def test_1() {
    val selected = s"!Array().${START}isEmpty$END"
    check(selected)
    val text = "!Array().isEmpty"
    val result = "Array().nonEmpty"
    testFix(text, result, hint)
  }

  def test_2() {
    val selected = s"!Option(1).${START}isEmpty$END"
    check(selected)
    val text = "!Option(1).isEmpty"
    val result = "Option(1).nonEmpty"
    testFix(text, result, hint)
  }

  override val inspectionClass = classOf[NotIsEmptyInspection]
}
