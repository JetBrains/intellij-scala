package org.jetbrains.plugins.scala.codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.InspectionBundle

/**
 * @author Markus.Hauck
 */
class SortedMaxInspectionTest extends OperationsOnCollectionInspectionTest {
  override val inspectionClass: Class[_ <: OperationOnCollectionInspection] = classOf[SortedMaxMinInspection]

  override def hint: String = InspectionBundle.message("replace.sorted.head.with.min")

  def test(): Unit = {
    doTest(
      s"Seq(1,2,3).${START}sorted.head$END",
      "Seq(1,2,3).sorted.head",
      "Seq(1, 2, 3).min"
    )
  }
}

class SortedMinInspectionTest extends OperationsOnCollectionInspectionTest {
  override val inspectionClass: Class[_ <: OperationOnCollectionInspection] = classOf[SortedMaxMinInspection]

  override def hint: String = InspectionBundle.message("replace.sorted.last.with.max")

  def test(): Unit = {
    doTest(
      s"Seq(1,2,3).${START}sorted.last$END",
      "Seq(1,2,3).sorted.last",
      "Seq(1, 2, 3).max"
    )
  }
}

class SortByMaxInspectionTest extends OperationsOnCollectionInspectionTest {
  override val inspectionClass: Class[_ <: OperationOnCollectionInspection] = classOf[SortedMaxMinInspection]

  override def hint: String = InspectionBundle.message("replace.sortBy.head.with.minBy")

  def test(): Unit = {
    doTest(
      s"Seq(1,2,3).${START}sortBy(_.toString).head$END",
      "Seq(1,2,3).sortBy(_.toString).head",
      "Seq(1, 2, 3).minBy(_.toString)"
    )
  }
}

class SortByMinInspectionTest extends OperationsOnCollectionInspectionTest {
  override val inspectionClass: Class[_ <: OperationOnCollectionInspection] = classOf[SortedMaxMinInspection]

  override def hint: String = InspectionBundle.message("replace.sortBy.last.with.maxBy")

  def test(): Unit = {
    doTest(
      s"Seq(1,2,3).${START}sortBy(_.toString).last$END",
      "Seq(1,2,3).sortBy(_.toString).last",
      "Seq(1, 2, 3).maxBy(_.toString)"
    )
  }
}
