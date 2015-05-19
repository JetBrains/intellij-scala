package org.jetbrains.plugins.scala.codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.InspectionBundle

/**
 * @author Nikolay.Tropin
 */
class ToSetAndBackTest extends OperationsOnCollectionInspectionTest {
  override val inspectionClass: Class[_ <: OperationOnCollectionInspection] = classOf[ToSetAndBackInspection]

  override def hint: String = InspectionBundle.message("replace.toSet.and.back.with.distinct")

  def testSeq(): Unit = {
    doTest(
      s"Seq(1).${START}toSet.toSeq$END",
      "Seq(1).toSet.toSeq",
      "Seq(1).distinct"
    )
  }

  def testList(): Unit = {
    doTest(
      s"List(1).${START}toSet.toList$END",
      "List(1).toSet.toList",
      "List(1).distinct"
    )
  }

  def testArray(): Unit = {
    doTest(
      s"Array(1).${START}toSet.toArray[Int]$END",
      "Array(1).toSet.toArray[Int]",
      "Array(1).distinct"
    )
  }

  def testPostfix(): Unit = {
    doTest(
      s"(Seq(1)$START toSet) toSeq$END",
      "(Seq(1) toSet) toSeq",
      "Seq(1).distinct"
    )
  }

  def testTo(): Unit = {
    doTest(
      s"Seq(1).${START}toSet.to[Seq]$END",
      "Seq(1).toSet.to[Seq]",
      "Seq(1).distinct"
    )
  }

  def testMap(): Unit = {
    checkTextHasNoErrors("Map(1 -> 2).toSet.toSeq")
  }

  def testSeqToList(): Unit = {
    checkTextHasNoErrors("Seq(1).toSet.toList")
  }

  def testSeqToList2(): Unit = {
    checkTextHasNoErrors("Seq(1).toSet.to[List]")
  }
}
