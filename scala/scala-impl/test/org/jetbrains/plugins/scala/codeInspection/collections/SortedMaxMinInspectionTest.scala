package org.jetbrains.plugins.scala
package codeInspection
package collections

abstract class SortedMaxMinInspectionTest extends OperationsOnCollectionInspectionTest {
  override val classOfInspection: Class[_ <: OperationOnCollectionInspection] =
    classOf[SortedMaxMinInspection]
}

class SortedMaxInspectionTest extends SortedMaxMinInspectionTest {

  override protected val hint: String =
    ScalaInspectionBundle.message("replace.sorted.head.with.min")

  def test(): Unit = {
    doTest(
      s"Seq(1,2,3).${START}sorted.head$END",
      "Seq(1,2,3).sorted.head",
      "Seq(1, 2, 3).min"
    )
  }
}

class SortedMinInspectionTest extends SortedMaxMinInspectionTest {

  override protected val hint: String =
    ScalaInspectionBundle.message("replace.sorted.last.with.max")

  def test(): Unit = {
    doTest(
      s"Seq(1,2,3).${START}sorted.last$END",
      "Seq(1,2,3).sorted.last",
      "Seq(1, 2, 3).max"
    )
  }
}

class SortByMaxInspectionTest extends SortedMaxMinInspectionTest {

  override protected val hint: String =
    ScalaInspectionBundle.message("replace.sortBy.head.with.minBy")

  def test(): Unit = {
    doTest(
      s"Seq(1,2,3).${START}sortBy(_.toString).head$END",
      "Seq(1,2,3).sortBy(_.toString).head",
      "Seq(1, 2, 3).minBy(_.toString)"
    )
  }
}

class SortByMinInspectionTest extends SortedMaxMinInspectionTest {

  override protected val hint: String =
    ScalaInspectionBundle.message("replace.sortBy.last.with.maxBy")

  def test(): Unit = {
    doTest(
      s"Seq(1,2,3).${START}sortBy(_.toString).last$END",
      "Seq(1,2,3).sortBy(_.toString).last",
      "Seq(1, 2, 3).maxBy(_.toString)"
    )
  }
}
