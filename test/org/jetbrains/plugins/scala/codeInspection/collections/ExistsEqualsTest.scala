package org.jetbrains.plugins.scala
package codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.InspectionBundle

/**
 * Nikolay.Tropin
 * 2014-05-06
 */
class ExistsEqualsTest extends OperationsOnCollectionInspectionTest{
  val hint = InspectionBundle.message("exists.equals.hint")
  def test_1() {
    val selected = s"List(0).${START}exists(x => x == 1)$END"
    check(selected)
    val text = "List(0).exists(x => x == 1)"
    val result = "List(0).contains(1)"
    testFix(text, result, hint)
  }

  def test_2() {
    val selected = s"List(0).${START}exists(_ == 1)$END"
    check(selected)
    val text = "List(0).exists(_ == 1)"
    val result = "List(0).contains(1)"
    testFix(text, result, hint)
  }

  def test_3() {
    val selected = s"List(0) ${START}exists (x => x == 1)$END"
    check(selected)
    val text = "List(0) exists (x => x == 1)"
    val result = "List(0) contains 1"
    testFix(text, result, hint)
  }

  def test_4() {
    val selected = s"List(0).${START}exists(1 == _)$END"
    check(selected)
    val text = "List(0).exists(1 == _)"
    val result = "List(0).contains(1)"
    testFix(text, result, hint)
  }

  def test_5() {
    val text = "List(0).exists(x => x == - x)"
    checkTextHasNoErrors(text, hint, inspectionClass)
  }

  def test_6() {
    val text = "Some(1).exists(_ == 1)"
    checkTextHasNoErrors(text, hint, inspectionClass)
  }

  def test_7() {
    val text = "Map(1 -> \"1\").exists(_ == (1, \"1\"))"
    checkTextHasNoErrors(text, hint, inspectionClass)
  }

  override val inspectionClass = classOf[ExistsEqualsInspection]
}
