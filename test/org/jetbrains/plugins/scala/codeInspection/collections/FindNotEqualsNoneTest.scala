package org.jetbrains.plugins.scala
package codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.InspectionBundle

/**
 * Nikolay.Tropin
 * 5/30/13
 */
class FindNotEqualsNoneTest extends OperationsOnCollectionInspectionTest {
  val hint = InspectionBundle.message("find.notEquals.none.hint")
  def test_1() {
    val selected = s"(Nil ${START}find (_ => true)) != None$END"
    check(selected)
    val text = "(Nil find (_ => true)) != None"
    val result = "Nil exists (_ => true)"
    testFix(text, result, hint)
  }

  def test_2() {
    val selected = s"Nil.${START}find(_ => true) != None$END"
    check(selected)
    val text = "Nil.find(_ => true) != None"
    val result = "Nil.exists(_ => true)"
    testFix(text, result, hint)
  }

  override val inspectionClass = classOf[FindNotEqualsNoneInspection]
}
