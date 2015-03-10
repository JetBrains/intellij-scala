package org.jetbrains.plugins.scala
package codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.InspectionBundle

/**
 * Nikolay.Tropin
 * 5/30/13
 */
class FilterSizeTest extends OperationsOnCollectionInspectionTest {
  val hint = InspectionBundle.message("filter.size.hint")
  def test_1() {
    val selected = s"Array().${START}filter(x => true).size$END"
    check(selected)
    val text = "Array().filter(x => true).size"
    val result = "Array().count(x => true)"
    testFix(text, result, hint)
  }

  def test_2() {
    val selected = s"List().${START}filter(x => true).length$END"
    check(selected)
    val text = "List().filter(x => true).length"
    val result = "List().count(x => true)"
    testFix(text, result, hint)
  }

  def test_3() {
    val selected = s"Map() ${START}filter (x => true) size$END"
    check(selected)
    val text = "Map() filter (x => true) size"
    val result = "Map() count (x => true)"
    testFix(text, result, hint)
  }

  def test_4() {
    val selected = s"List().${START}filter {x => true}.size$END"
    check(selected)
    val text =
      """List().filter {
        |  x => true
        |}.size
        |""".stripMargin
    val result =
      """List().count {
        |  x => true
        |}
        |""".stripMargin
    testFix(text, result, hint)
  }


  override val inspectionClass = classOf[FilterSizeInspection]
}
