package org.jetbrains.plugins.scala
package codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.InspectionBundle

/**
 * Nikolay.Tropin
 * 2014-05-06
 */
class GetOrElseNullTest extends OperationsOnCollectionInspectionTest {
  val hint: String = InspectionBundle.message("getOrElse.null.hint")

  def test_1() {
    val selected = s"None.${START}getOrElse(null)$END"
    check(selected)

    val text = "None.getOrElse(null)"
    val result = "None.orNull"
    testFix(text, result, hint)
  }

  def test_2() {
    val selected = s"None ${START}getOrElse null$END"
    check(selected)

    val text = "None getOrElse null"
    val result = "None.orNull"
    testFix(text, result, hint)
  }

  def test_3(): Unit = {
    val selected = s"Some(1) orElse Some(2) ${START}getOrElse null$END"
    check(selected)

    val text = "Some(1) orElse Some(2) getOrElse null"
    val result = "(Some(1) orElse Some(2)).orNull"
    testFix(text, result, hint)
  }

  override val inspectionClass = classOf[GetOrElseNullInspection]
}
