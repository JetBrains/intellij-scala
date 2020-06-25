package org.jetbrains.plugins.scala
package codeInspection
package collections

import com.intellij.testFramework.EditorTestUtil

/**
 * @author Nikolay.Tropin
 */
class MapFlattenTest extends OperationsOnCollectionInspectionTest {

  override protected val classOfInspection: Class[_ <: OperationOnCollectionInspection] =
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

  def testSCL10574(): Unit = {
    checkTextHasNoErrors("Seq(1).map(Option.apply).flatten")
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
