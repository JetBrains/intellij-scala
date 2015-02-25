package org.jetbrains.plugins.scala.codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.InspectionBundle

/**
 * @author Nikolay.Tropin
 */
class RedundantConversionTest extends OperationsOnCollectionInspectionTest{
  override val inspectionClass: Class[_ <: OperationOnCollectionInspection] = classOf[RedundantCollectionConversionInspection]

  override def hint: String = InspectionBundle.message("redundant.collection.conversion")

  def test_1() {
    doTest(s"List(1, 2).${START}toList$END",
      "List(1, 2).toList",
      "List(1, 2)")
  }

  def test_2() {
    doTest(s"Seq(1, 2).${START}to[Seq]$END",
      "Seq(1, 2).to[Seq]",
      "Seq(1, 2)")
  }

  def test_3(): Unit = {
    doTest(s"Map(1 -> true).${START}toMap[Int, Boolean]$END",
      "Map(1 -> true).toMap[Int, Boolean]",
      "Map(1 -> true)")
  }

  def test_4(): Unit = {
    doTest(
      s"""
         |def list() = List(1, 2)
         |list().${START}toList$END
       """.stripMargin,
      """
         |def list() = List(1, 2)
         |list().toList
       """.stripMargin,
      """
        |def list() = List(1, 2)
        |list()
      """.stripMargin
    )
  }
}
