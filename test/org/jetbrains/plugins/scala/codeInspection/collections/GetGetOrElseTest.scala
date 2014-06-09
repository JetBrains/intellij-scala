package org.jetbrains.plugins.scala
package codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.InspectionBundle

/**
 * Nikolay.Tropin
 * 2014-05-08
 */
class GetGetOrElseTest extends OperationsOnCollectionInspectionTest {
  val hint = InspectionBundle.message("get.getOrElse.hint")
  override val inspectionClass = classOf[GetGetOrElseInspection]

  def test_1() {
    val selected = s"""Map().${START}get(0).getOrElse("")$END"""
    check(selected)
    val text = "Map().get(0).getOrElse(\"\")"
    val result = "Map().getOrElse(0, \"\")"
    testFix(text, result, hint)
  }

  def test_2() {
    val selected = s"""Map("a" -> "A") ${START}get "b" getOrElse "B"$END"""
    check(selected)
    val text = """Map("a" -> "A") get "b" getOrElse "B""""
    val result = """Map("a" -> "A").getOrElse("b", "B")"""
    testFix(text, result, hint)
  }
}
