package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

class WidenFunctionAndTupleTypes extends TypeInferenceTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version >= LatestScalaVersions.Scala_2_13

  def testSCL16384(): Unit = doTest(
    s"""
      |object Test {
      |def pearson[A](data: List[A])(fx: A => Double, fy: A => Double): Double = {
      |    import math.sqrt
      |    val (sX, rsX, sY, rsY, pXY, n) =
      |      data.foldLeft($START(0.0, 0.0, 0.0, 0.0, 0.0, 0)$END) { (acc, e) =>
      |        val (sX, rsX, sY, rsY, pXY, n) = acc
      |        val x = fx(e)
      |        val y = fy(e)
      |        (sX + x, rsX + x*x, sY + y, rsY + y*y, pXY + x*y, n + 1)
      |      }
      |    val numer = n * (pXY) - sX * sY
      |    val denom = sqrt(n * rsX - sX*sX) * sqrt(n * rsY - sY*sY)
      |    numer / denom
      |  }
      |}
      |//(Double, Double, Double, Double, Double, Int)
      |""".stripMargin
  )

  def testFunctionLiteral(): Unit = doTest(
    s"""
      |object Test {
      |  val a = $START(x: String) => 123$END
      |}
      |//String => Int
      |""".stripMargin
  )

  def testSCL17648(): Unit = doTest(
    s"""
       |object A {
       |trait Job { val length: Long; val weight: Long }
       |  def wct3(jobs: Array[Job]): Long = (jobs foldLeft(0L, 0L)) { (r, j) =>
       |    ${START}r$END
       |    ???
       |  }._2
       |}
       |//(Long, Long)
       |""".stripMargin
  )
}
