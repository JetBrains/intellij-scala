package org.jetbrains.plugins.scala
package codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.InspectionBundle

/**
 * Nikolay.Tropin
 * 2014-05-07
 */
class FilterSizeCheckTest extends OperationsOnCollectionInspectionTest {
  val hint = InspectionBundle.message("filter.size.check.hint")
  def test_1() {
    val selected = s"Array().${START}filter(x => true).size > 0$END"
    check(selected)
    val text = "Array().filter(x => true).size > 0"
    val result = "Array().exists(x => true)"
    testFix(text, result, hint)
  }

  def test_2() {
    val selected = s"List().${START}filter(x => true).length >= 1$END"
    check(selected)
    val text = "List().filter(x => true).length >= 1"
    val result = "List().exists(x => true)"
    testFix(text, result, hint)
  }

  def test_3() {
    val selected = s"(Map() ${START}filter (x => true)).size == 0$END"
    check(selected)
    val text = "(Map() filter (x => true)).size == 0"
    val result = "!(Map() exists (x => true))"
    testFix(text, result, hint)
  }

  def test_4() {
    val text = "Seq(0).filter(_ > 0).size == 1"
    checkTextHasNoErrors(text, hint, inspectionClass)
  }

  def test_5() {
    val text = "Seq(0).filter(_ > 0).size + 1"
    checkTextHasNoErrors(text, hint, inspectionClass)
  }

  def testWithSideEffect(): Unit = {
    val text =
      """var z = 1
        |Seq(0).filter { x =>
        |  z = x
        |  true
        |}.size > 0
      """.stripMargin
    checkTextHasNoErrors(text)
  }

  override val inspectionClass = classOf[FilterSizeCheckInspection]
}
