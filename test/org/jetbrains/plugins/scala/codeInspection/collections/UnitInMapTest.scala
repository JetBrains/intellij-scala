package org.jetbrains.plugins.scala.codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.InspectionBundle

/**
 * @author Nikolay.Tropin
 */
class UnitInMapTest extends OperationsOnCollectionInspectionTest {
  override val inspectionClass: Class[_ <: OperationOnCollectionInspection] = classOf[UnitInMapInspection]

  override def description: String = InspectionBundle.message("expression.unit.return.in.map")
  override def hint: String = InspectionBundle.message("use.foreach.instead.of.map")

  def test1(): Unit = {
    doTest(
      s"""
        |Seq("1", "2").map {x =>
        |  if (x.startsWith("1")) x
        |  else $START{
        |    val y = x + 2
        |  }$END
        |}
      """.stripMargin,
      """
        |Seq("1", "2").map {x =>
        |  if (x.startsWith("1")) x
        |  else {
        |    val y = x + 2
        |  }
        |}
      """.stripMargin,
      """
        |Seq("1", "2").foreach {x =>
        |  if (x.startsWith("1")) x
        |  else {
        |    val y = x + 2
        |  }
        |}
      """.stripMargin
    )
  }

  def test2(): Unit = {
    check(s"val mapped = Seq(1, 2).map(${START}println(_)$END)")
    check(
      s"""
         |Seq(1, 2).map {
         |  ${START}println(_)$END
         |}
       """.stripMargin)
    check(
      s"""
         |Seq(1, 2).map { x =>
         |  ${START}println(x)$END
         |}
       """.stripMargin)
  }
}
