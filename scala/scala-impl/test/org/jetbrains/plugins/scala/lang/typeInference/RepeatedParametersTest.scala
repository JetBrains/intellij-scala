package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

class RepeatedParametersTest extends ScalaLightCodeInsightFixtureTestAdapter {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_2_13

  def testSCL16016(): Unit = checkTextHasNoErrors(
    """
      |def test(ii: Int*): Unit = {
      |  val x: Seq[Int] = ii
      |}
      |""".stripMargin
  )
}

class UnapplySeqRepeatedParametersTest extends ScalaLightCodeInsightFixtureTestAdapter {
  override protected def supportedIn(version: ScalaVersion): Boolean = version >= LatestScalaVersions.Scala_2_11

  def testSCL16110(): Unit = checkTextHasNoErrors(
    """
      |object BugDemo {
      |  case class Cell(value: Int) {
      |    def text: String = value.toString
      |  }
      |  val cells: List[Cell] = List(Cell(1))
      |  val List(cell) = cells // Error: IDEA inferred type for 'cell' as Any instead of 'Cell'
      |  cell.text // Error: 'text' is highlighted as an error (because the incorrect inference)
      |}
      |""".stripMargin
  )

  def testSCL15627(): Unit = checkTextHasNoErrors(
    """
      |object Main {
      |  val aSeq = Seq(1, 2, 3)
      |  val sum = aSeq match {
      |    case Seq(a, b, c) => a + b + c // Cannot resolve symbol +
      |  }
      |}
      |""".stripMargin
  )
}




