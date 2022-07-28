package org.jetbrains.plugins.scala
package codeInspection
package collections

class FilterSizeTest extends OperationsOnCollectionInspectionTest {
  override val hint = ScalaInspectionBundle.message("filter.size.hint")
  def test_1(): Unit = {
    val selected = s"Array().${START}filter(x => true).size$END"
    checkTextHasError(selected)
    val text = "Array().filter(x => true).size"
    val result = "Array().count(x => true)"
    testQuickFix(text, result, hint)
  }

  def test_2(): Unit = {
    val selected = s"List().${START}filter(x => true).length$END"
    checkTextHasError(selected)
    val text = "List().filter(x => true).length"
    val result = "List().count(x => true)"
    testQuickFix(text, result, hint)
  }

  def test_3(): Unit = {
    val selected = s"Map(1 -> 2) ${START}filter (x => true) size$END"
    checkTextHasError(selected)
    val text = "Map(1 -> 2) filter (x => true) size"
    val result = "Map(1 -> 2) count (x => true)"
    testQuickFix(text, result, hint)
  }

  def test_4(): Unit = {
    val selected = s"List().${START}filter {x => true}.size$END"
    checkTextHasError(selected)
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
    testQuickFix(text, result, hint)
  }

  def test_SCL15437(): Unit = {
    val selected =
      s"""
        |trait LengthTest {
        |  def foo(): Unit = {
        |    Seq().${START}filter(_ => true).size$END
        |
        |
        |  }
        |}
      """.stripMargin
    checkTextHasError(selected)

    val text =
      """
        |trait LengthTest {
        |  def foo(): Unit = {
        |    Seq().filter(_ => true).size
        |
        |
        |  }
        |}
      """.stripMargin

    val result =
      """
        |trait LengthTest {
        |  def foo(): Unit = {
        |    Seq().count(_ => true)
        |
        |
        |  }
        |}
      """.stripMargin
    testQuickFix(text, result, hint)
  }

  override val classOfInspection = classOf[FilterSizeInspection]
}
