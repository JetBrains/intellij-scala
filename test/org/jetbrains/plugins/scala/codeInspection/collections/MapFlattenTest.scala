package org.jetbrains.plugins.scala.codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.InspectionBundle

/**
 * @author Nikolay.Tropin
 */
class MapFlattenTest extends OperationsOnCollectionInspectionTest {
  override val inspectionClass: Class[_ <: OperationOnCollectionInspection] = classOf[MapFlattenInspection]

  override def hint: String = InspectionBundle.message("replace.map.flatten.with.flatMap")

  def test1(): Unit = {
    doTest(
      s"Seq().${START}map(Seq(_)).flatten$END",
      "Seq().map(Seq(_)).flatten",
      "Seq().flatMap(Seq(_))"
    )
  }

  def testInfix(): Unit = {
    doTest(
      s"Seq() ${START}map (x => Seq(x)) flatten$END",
      "Seq() map (x => Seq(x)) flatten",
      "Seq() flatMap (x => Seq(x))"
    )
  }

  def testArray(): Unit = {
    doTest(
      s"Array().${START}map(Seq(_)).flatten$END",
      "Array().map(Seq(_)).flatten",
      "Array().flatMap(Seq(_))"
    )
  }

  def testArrayInside(): Unit = {
    doTest(
      s"Seq(1).${START}map(x => Array(x)).flatten$END",
      "Seq(1).map(x => Array(x)).flatten",
      "Seq(1).flatMap(x => Array(x))"
    )
  }

  def testStringInside(): Unit = {
    doTest(
      s"Seq(1).${START}map(_.toString).flatten$END",
      "Seq(1).map(_.toString).flatten",
      "Seq(1).flatMap(_.toString)"
    )
  }
}
