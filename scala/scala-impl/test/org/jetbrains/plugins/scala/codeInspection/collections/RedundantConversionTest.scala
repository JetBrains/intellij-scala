package org.jetbrains.plugins.scala
package codeInspection
package collections

abstract class RedundantConversionTestBase extends OperationsOnCollectionInspectionTest {

  override protected val classOfInspection: Class[? <: OperationOnCollectionInspection] =
    classOf[RedundantCollectionConversionInspection]

  override protected val hint: String =
    ScalaInspectionBundle.message("redundant.collection.conversion")

  def test_1(): Unit = {
    doTest(s"List(1, 2).${START}toList$END",
      "List(1, 2).toList",
      "List(1, 2)")
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

  def test_5(): Unit = {
    doTest(
      s"""Seq(1) match {
         |  case seq => seq.${START}toSeq$END
         |}""".stripMargin,
      s"""Seq(1) match {
         |  case seq => seq.toSeq
         |}""".stripMargin,
      s"""Seq(1) match {
         |  case seq => seq
         |}""".stripMargin

    )
  }

  // SCL-18210
  def test_toUpperCase_is_not_redundant(): Unit = checkTextHasNoErrors(
    """
      |val x = "test".toUpperCase
      |""".stripMargin
  )

  // SCL-18210
  def test_toLowerCase_is_not_redundant(): Unit = checkTextHasNoErrors(
    """
      |val x = "test".toLowerCase
      |""".stripMargin
  )
}

class RedundantConversionTest extends RedundantConversionTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean = version <= LatestScalaVersions.Scala_2_12

  def test_to(): Unit = {
    doTest(s"Seq(1, 2).${START}to[Seq]$END",
      "Seq(1, 2).to[Seq]",
      "Seq(1, 2)")
  }

  def test_to_with_implicit_generic(): Unit = {
    doTest(s"val x: List[Int] = List(1, 2).${START}to$END",
      "val x: List[Int] = List(1, 2).to",
      "val x: List[Int] = List(1, 2)")
  }
}


class RedundantConversionTest_2_13 extends RedundantConversionTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean = version >= LatestScalaVersions.Scala_2_13

  // TODO: implement inspection for .to(Factory)
  /*
  def test_to(): Unit = {
    doTest(s"Seq(1, 2).${START}to(Seq)$END",
      "Seq(1, 2).to(Seq)",
      "Seq(1, 2)")
  }

  def test_to_with_implicit_generic(): Unit = {
    doTest(s"val x: List[Int] = List(1, 2).${START}to$END",
      "val x: List[Int] = List(1, 2).to",
      "val x: List[Int] = List(1, 2)")
  }
   */
}
