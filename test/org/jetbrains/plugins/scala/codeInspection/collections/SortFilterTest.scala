package org.jetbrains.plugins.scala
package codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.InspectionBundle

/**
 * Nikolay.Tropin
 * 1/24/14
 */
class SortFilterTest extends OperationsOnCollectionInspectionTest {
  override def hint: String = InspectionBundle.message("sort.filter.hint")

  def testWithoutParams() {
    val selected = s"List(0, 1).${START}sorted.filter(_ => true)$END"
    check(selected)

    val text = "List(0, 1).sorted.filter(_ => true)"
    val result = "List(0, 1).filter(_ => true).sorted"
    testFix(text, result, hint)
  }

  def testWithParameter() {
    val selected = s"List(0, 1).${START}sortWith((x, y) => x < y).filter(_ => true)$END"
    check(selected)

    val text = "List(0, 1).sortWith((x, y) => x < y).filter(_ => true)"
    val result = "List(0, 1).filter(_ => true).sortWith((x, y) => x < y)"
    testFix(text, result, hint)
  }

  def testWithGenericParameter() {
    val selected = s"List(0, 1).${START}sortBy[String](_.toString).filter(_ => true)$END"
    check(selected)

    val text = "List(0, 1).sortBy[String](_.toString).filter(_ => true)"
    val result = "List(0, 1).filter(_ => true).sortBy[String](_.toString)"
    testFix(text, result, hint)
  }

  def testInfix() {
    val selected = s"List(0, 1).${START}sortBy[String](_.toString) filter (_ => true)$END"
    check(selected)

    val text = "List(0, 1).sortBy[String](_.toString) filter (_ => true)"
    val result = "List(0, 1).filter(_ => true).sortBy[String](_.toString)"
    testFix(text, result, hint)
  }

  def testWithSideEffect(): Unit = {
    checkTextHasNoErrors(
      """
        |var q = 1
        |Seq(3, 1, 2).sorted.filter {
        |  i =>
        |    q += 1
        |    i % 2 == 0
        |}
      """.stripMargin)
  }

  override val inspectionClass = classOf[SortFilterInspection]
}
