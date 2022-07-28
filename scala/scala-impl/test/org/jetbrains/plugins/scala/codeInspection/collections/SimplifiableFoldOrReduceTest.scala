package org.jetbrains.plugins.scala
package codeInspection
package collections

class FoldSumTest extends OperationsOnCollectionInspectionTest {

  override protected val classOfInspection: Class[_ <: OperationOnCollectionInspection] =
    classOf[SimplifiableFoldOrReduceInspection]

  override protected val hint: String =
    ScalaInspectionBundle.message("fold.sum.hint")

  def test_1(): Unit = {
    val selected = s"List(0).$START/:(0)(_ + _)$END"
    checkTextHasError(selected)
    val text = "List(0)./:(0)(_ + _)"
    val result = "List(0).sum"
    testQuickFix(text, result, hint)
  }

  def test_2(): Unit = {
    val selected = s"Array(0).${START}fold(0) ((_:Int) + _)$END"
    checkTextHasError(selected)
    val text = "Array(0).fold(0) ((_:Int) + _)"
    val result = "Array(0).sum"
    testQuickFix(text, result, hint)
  }

  def test_3(): Unit = {
    val selected = s"List(0).${START}foldLeft[Int](0) {(x,y) => x + y}$END"
    checkTextHasError(selected)
    val text = "List(0).foldLeft[Int](0) {(x,y) => x + y}"
    val result = "List(0).sum"
    testQuickFix(text, result, hint)
  }

  def test_4(): Unit = {
    val text = s"""List("a").foldLeft(0)(_ + _)"""
    checkTextHasNoErrors(text)
  }
}

class ReduceMinTest extends OperationsOnCollectionInspectionTest {

  override protected val classOfInspection: Class[_ <: OperationOnCollectionInspection] =
    classOf[SimplifiableFoldOrReduceInspection]

  override protected val hint: String =
    ScalaInspectionBundle.message("reduce.min.hint")

  def test_1(): Unit = {
    val text = "List(1, 2, 3).reduceLeft(_ min _)"
    val result = "List(1, 2, 3).min"
    testQuickFix(text, result, hint)
  }

  def test_2(): Unit = {
    val text = "List(1, 2, 3).reduce((x, y) => math.min(x, y))"
    val result = "List(1, 2, 3).min"
    testQuickFix(text, result, hint)
  }

  def test_3(): Unit = {
    val text =
      """class A {def min(other: A): A = this}
        |List(new A).reduce(_ min _)""".stripMargin
    checkTextHasNoErrors(text)
  }
}

class ReduceProductTest extends OperationsOnCollectionInspectionTest {

  override protected val classOfInspection: Class[_ <: OperationOnCollectionInspection] =
    classOf[SimplifiableFoldOrReduceInspection]

  override protected val hint: String =
    ScalaInspectionBundle.message("reduce.product.hint")

  def test_1(): Unit = {
    val text = "List(1, 2, 3).reduceLeft(_ * _)"
    val result = "List(1, 2, 3).product"
    testQuickFix(text, result, hint)
  }

  def test_2(): Unit = {
    val text = "List(1, 2, 3).reduce((x, y) => x * y)"
    val result = "List(1, 2, 3).product"
    testQuickFix(text, result, hint)
  }

  def test_3(): Unit = {
    val text = "List(\"a\").reduce(_ * _)"
    checkTextHasNoErrors(text)
  }
}