package org.jetbrains.plugins.scala
package codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.InspectionBundle

/**
 * Nikolay.Tropin
 * 2014-05-07
 */
class DropOneTest extends OperationsOnCollectionInspectionTest {
  val hint = InspectionBundle.message("drop.one.hint")
  def test_1() {
    val selected = s"Array(1).${START}drop(1)$END"
    check(selected)
    val text = "Array(1).drop(1)"
    val result = "Array(1).tail"
    testFix(text, result, hint)
  }

  def test_2() {
    val selected = s"List(1) ${START}drop 1$END"
    check(selected)
    val text = "List(1) drop 1"
    val result = "List(1).tail"
    testFix(text, result, hint)
  }

  override val inspectionClass = classOf[DropOneInspection]
}

