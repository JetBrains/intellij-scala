package org.jetbrains.plugins.scala
package codeInspection
package collections

abstract class ToSetAndBackTestBase extends OperationsOnCollectionInspectionTest {

  override protected val classOfInspection: Class[_ <: OperationOnCollectionInspection] =
    classOf[ToSetAndBackInspection]

  override protected val hint: String =
    ScalaInspectionBundle.message("replace.toSet.and.back.with.distinct")

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
      s"(Seq(1) ${START}toSet) toSeq$END",
      "(Seq(1) toSet) toSeq",
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

class ToSetAndBackTest_2_12 extends ToSetAndBackTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version <= LatestScalaVersions.Scala_2_12

  def testTo(): Unit = {
    doTest(
      s"Seq(1).${START}toSet.to[Seq]$END",
      "Seq(1).toSet.to[Seq]",
      "Seq(1).distinct"
    )
  }
}

class ToSetAndBackTest_2_13 extends ToSetAndBackTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version >= LatestScalaVersions.Scala_2_12

  def testTo(): Unit = {
    doTest(
      s"Seq(1).${START}toSet.to(Seq)$END",
      "Seq(1).toSet.to(Seq)",
      "Seq(1).distinct"
    )
  }
}
