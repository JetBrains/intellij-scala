package org.jetbrains.plugins.scala
package codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.InspectionBundle

/**
 * Nikolay.Tropin
 * 5/30/13
 */
class FoldLeftSumTest extends OperationsOnCollectionInspectionTest {
  val hint = InspectionBundle.message("foldLeft.sum.hint")

  def test_1() {
    val selected = s"List(0).$START/:(0)(_ + _)$END"
    check(selected)
    val text = "List(0)./:(0)(_ + _)"
    val result = "List(0).sum"
    testFix(text, result, hint)
  }

  def test_2() {
    val selected = s"Array(0).${START}foldLeft(0) ((_:Int) + _)$END"
    check(selected)
    val text = "Array(0).foldLeft(0) ((_:Int) + _)"
    val result = "Array(0).sum"
    testFix(text, result, hint)
  }

  def test_3() {
    val selected = s"List(0).${START}foldLeft[Int](0) {(x,y) => x + y}$END"
    check(selected)
    val text = "List(0).foldLeft[Int](0) {(x,y) => x + y}"
    val result = "List(0).sum"
    testFix(text, result, hint)
  }

  def test_4() {
    val text = s"""List("a").foldLeft(0)(_ + _)"""
    checkTextHasNoErrors(text, annotation, inspectionClass)
  }

  override val inspectionClass = classOf[FoldLeftSumInspection]
}
