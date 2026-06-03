package org.jetbrains.plugins.scala
package codeInspection
package collections

class GetOrElseNullTest extends OperationsOnCollectionInspectionTest {

  override val hint: String = ScalaInspectionBundle.message("getOrElse.null.hint")

  def test_1(): Unit = {
    val selected = s"None.${START}getOrElse(null)$END"
    checkTextHasError(selected)

    val text = "None.getOrElse(null)"
    val result = "None.orNull"
    testQuickFix(text, result, hint)
  }

  def test_2(): Unit = {
    val selected = s"None ${START}getOrElse null$END"
    checkTextHasError(selected)

    val text = "None getOrElse null"
    val result = "None.orNull"
    testQuickFix(text, result, hint)
  }

  def test_3(): Unit = {
    val selected = s"Some(1) orElse Some(2) ${START}getOrElse null$END"
    checkTextHasError(selected)

    val text = "Some(1) orElse Some(2) getOrElse null"
    val result = "(Some(1) orElse Some(2)).orNull"
    testQuickFix(text, result, hint)
  }

  override val classOfInspection = classOf[GetOrElseNullInspection]
}
