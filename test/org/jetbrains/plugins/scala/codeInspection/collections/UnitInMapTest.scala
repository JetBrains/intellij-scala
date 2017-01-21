package org.jetbrains.plugins.scala
package codeInspection
package collections

import com.intellij.testFramework.EditorTestUtil

/**
 * @author Nikolay.Tropin
 */
class UnitInMapTest extends OperationsOnCollectionInspectionTest {

  import EditorTestUtil.{SELECTION_END_TAG => END, SELECTION_START_TAG => START}

  override val classOfInspection: Class[_ <: OperationOnCollectionInspection] =
    classOf[UnitInMapInspection]

  override protected lazy val description: String =
    InspectionBundle.message("expression.unit.return.in.map")

  override protected val hint: String =
    InspectionBundle.message("use.foreach.instead.of.map")

  def test1(): Unit = {
    doTest(
      s"""
        |Seq("1", "2").map { x =>
        |  if (x.startsWith("1")) x
        |  else $START{
        |    val y = x + 2
        |  }$END
        |}
      """.stripMargin,
      """
        |Seq("1", "2").map { x =>
        |  if (x.startsWith("1")) x
        |  else {
        |    val y = x + 2
        |  }
        |}
      """.stripMargin,
      """
        |Seq("1", "2").foreach { x =>
        |  if (x.startsWith("1")) x
        |  else {
        |    val y = x + 2
        |  }
        |}
      """.stripMargin
    )
  }

  def test2(): Unit = {
    checkTextHasError(s"val mapped = Seq(1, 2).map(${START}println(_)$END)")
    checkTextHasError(
      s"""
         |Seq(1, 2).map {
         |  ${START}println(_)$END
         |}
       """.stripMargin)
    checkTextHasError(
      s"""
         |Seq(1, 2).map { x =>
         |  ${START}println(x)$END
         |}
       """.stripMargin)
  }

  def testFunctionToFunctionToUnit(): Unit = {
    checkTextHasNoErrors(s"Seq(1, 2).map(x => $START() => println(x)$END)")
  }
}
