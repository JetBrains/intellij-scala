package org.jetbrains.plugins.scala
package codeInspection
package collections

abstract class MapFlattenTestBase extends OperationsOnCollectionInspectionTest {

  override protected def supportedIn(version: ScalaVersion): Boolean = version <= LatestScalaVersions.Scala_2_12

  override protected val classOfInspection: Class[? <: OperationOnCollectionInspection] =
    classOf[MapFlattenInspection]

  override protected val hint: String =
    ScalaInspectionBundle.message("replace.map.flatten.with.flatMap")

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

  def testSCL12675(): Unit = {
    checkTextHasNoErrors(
      """
        |val r = Map(1 -> List(1,2,3), 2 -> List(3,4,5))
        |r.map(n => n._2.map(z => (n._1, z))).flatten
      """.stripMargin)
  }

  def testSCL10483(): Unit ={
    checkTextHasNoErrors(
      """
        |def f(a: String, b: String) = a + b
        |val seq = Seq(("foo", "bar"))
        |seq.map((f _).tupled).flatten.headOption
      """.stripMargin)
  }
}

class MapFlattenTest_2_12 extends MapFlattenTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version <= LatestScalaVersions.Scala_2_12

  def testSCL10574(): Unit = {
    checkTextHasNoErrors("Seq(1).map(Option.apply).flatten")
  }
}

class MapFlattenTest_2_13 extends MapFlattenTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version > LatestScalaVersions.Scala_2_12

  def testSCL10574(): Unit = doTest(
    s"Seq(1).${START}map(Option.apply).flatten$END",
    "Seq(1).map(Option.apply).flatten",
    "Seq(1).flatMap(Option.apply)"
  )

}