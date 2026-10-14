package org.jetbrains.plugins.scala.lang.typeConformance

import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

class MatchableConformanceTest extends TypeConformanceTestBase {

  override protected def shouldPass: Boolean = true

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= LatestScalaVersions.Scala_3

  private def check(tpe: String, expected: Boolean): Unit =
    doTest(
      s"""val x: $tpe = ???
         |val m: Matchable = x
         |//$expected
         |""".stripMargin)

  def testInt(): Unit = check("Int", expected = true)
  def testAnyVal(): Unit = check("AnyVal", expected = true)
  def testString(): Unit = check("String", expected = true)
  def testAnyRef(): Unit = check("AnyRef", expected = true)
  def testList(): Unit = check("List[Int]", expected = true)
  def testNull(): Unit = check("Null", expected = true)
  def testLiteral(): Unit = check("1", expected = true)
  def testMatchable(): Unit = check("Matchable", expected = true)
  def testUnion(): Unit = check("Int | String", expected = true)
  def testUnionWithNull(): Unit = check("Int | Null", expected = true)
  def testIntersectionWithAny(): Unit = check("Int & Any", expected = true)

  def testAny(): Unit = check("Any", expected = false)
  def testUnionWithAny(): Unit = check("Int | Any", expected = false)

  def testTypeParameterBoundedByAny(): Unit =
    doTest(
      """def f[T](x: T): Unit = {
        |  val m: Matchable = x
        |}
        |//false
        |""".stripMargin)

  def testTypeParameterBoundedByMatchable(): Unit =
    doTest(
      """def f[T <: Matchable](x: T): Unit = {
        |  val m: Matchable = x
        |}
        |//true
        |""".stripMargin)
}
