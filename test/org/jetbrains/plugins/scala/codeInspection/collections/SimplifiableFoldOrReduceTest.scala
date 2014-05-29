package org.jetbrains.plugins.scala
package codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.InspectionBundle

/**
 * Nikolay.Tropin
 * 5/30/13
 */
class FoldSumTest extends OperationsOnCollectionInspectionTest {

  val hint = InspectionBundle.message("fold.sum.hint")
  override val inspectionClass = classOf[SimplifiableFoldOrReduceInspection]

  def test_1() {
    val selected = s"List(0).$START/:(0)(_ + _)$END"
    check(selected)
    val text = "List(0)./:(0)(_ + _)"
    val result = "List(0).sum"
    testFix(text, result, hint)
  }

  def test_2() {
    val selected = s"Array(0).${START}fold(0) ((_:Int) + _)$END"
    check(selected)
    val text = "Array(0).fold(0) ((_:Int) + _)"
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
    checkTextHasNoErrors(text, hint, inspectionClass)
  }
}

class ReduceMinTest extends OperationsOnCollectionInspectionTest {
  val hint = InspectionBundle.message("reduce.min.hint")
  override val inspectionClass = classOf[SimplifiableFoldOrReduceInspection]

  def test_1() {
    val text = "List(1, 2, 3).reduceLeft(_ min _)"
    val result = "List(1, 2, 3).min"
    testFix(text, result, hint)
  }

  def test_2() {
    val text = "List(1, 2, 3).reduce((x, y) => math.min(x, y))"
    val result = "List(1, 2, 3).min"
    testFix(text, result, hint)
  }

  def test_3() {
    val text = """class A {def min(other: A): A = this}
                 |List(new A).reduce(_ min _)""".stripMargin
    checkTextHasNoErrors(text, hint, inspectionClass)
  }
}

class ReduceProductTest extends OperationsOnCollectionInspectionTest {
  val hint = InspectionBundle.message("reduce.product.hint")
  override val inspectionClass = classOf[SimplifiableFoldOrReduceInspection]

  def test_1() {
    val text = "List(1, 2, 3).reduceLeft(_ * _)"
    val result = "List(1, 2, 3).product"
    testFix(text, result, hint)
  }

  def test_2() {
    val text = "List(1, 2, 3).reduce((x, y) => x * y)"
    val result = "List(1, 2, 3).product"
    testFix(text, result, hint)
  }

  def test_3() {
    val text = "List(\"a\").reduce(_ * _)"
    checkTextHasNoErrors(text, hint, inspectionClass)
  }
}