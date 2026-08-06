package org.jetbrains.plugins.scala
package codeInspection
package collections

abstract class MapToBooleanContainsInspectionTest extends OperationsOnCollectionInspectionTest {

  override protected val classOfInspection: Class[_ <: OperationOnCollectionInspection] =
    classOf[MapToBooleanContainsInspection]
}

class MapContainsTrueTest extends MapToBooleanContainsInspectionTest {

  override protected val hint: String =
    ScalaInspectionBundle.message("replace.map.contains.true.with.exists")

  def testSimple(): Unit = {
    doTest(
      s"Seq(1, 2).${START}map(_ => true).contains(true)$END",
      "Seq(1, 2).map(_ => true).contains(true)",
      "Seq(1, 2).exists(_ => true)"
    )
  }

  def testInfix(): Unit = {
    doTest(
      s"Seq(1, 2) ${START}map (_ => true) contains true$END",
      "Seq(1, 2) map (_ => true) contains true",
      "Seq(1, 2) exists (_ => true)"
    )
  }

  def testBlockArg(): Unit = {
    doTest(
      s"""
         |Seq(1, 2).${START}map { x =>
         |  true
         |}.contains(true)$END
       """.stripMargin,
      """
        |Seq(1, 2).map { x =>
        |  true
        |}.contains(true)
      """.stripMargin,
      """
        |Seq(1, 2).exists { x =>
        |  true
        |}
      """.stripMargin
    )
  }
}

class MapContainsFalseTest extends MapToBooleanContainsInspectionTest {

  override protected val hint: String =
    ScalaInspectionBundle.message("replace.map.contains.false.with.not.forall")

  def testSimple(): Unit = {
    doTest(
      s"Seq(1, 2).${START}map(_ => true).contains(false)$END",
      "Seq(1, 2).map(_ => true).contains(false)",
      "!Seq(1, 2).forall(_ => true)"
    )
  }

  def testInfix(): Unit = {
    doTest(
      s"Seq(1, 2) ${START}map (_ => true) contains false$END",
      "Seq(1, 2) map (_ => true) contains false",
      "!(Seq(1, 2) forall (_ => true))"
    )
  }

  def testBlockArg(): Unit = {
    doTest(
      s"""
         |Seq(1, 2).${START}map { x =>
         |  true
         |}.contains(false)$END
      """.stripMargin,
      """
        |Seq(1, 2).map { x =>
        |  true
        |}.contains(false)
      """.stripMargin,
      """
        |!Seq(1, 2).forall { x =>
        |  true
        |}
      """.stripMargin
    )
  }
}