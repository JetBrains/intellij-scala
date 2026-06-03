package org.jetbrains.plugins.scala
package codeInspection
package collections

/**
  * mattfowler
  * 4/28/16
  */
class FindAndMapToApplyTest extends OperationsOnCollectionInspectionTest {

  override protected val classOfInspection: Class[FindAndMapToGetInspection] =
    classOf[FindAndMapToGetInspection]

  override protected val hint: String =
    "Replace find and map with apply"

  def test_inline_map(): Unit = {
    val selected = s"Map(1 -> 2).${START}find(_._1 == 1).map(_._2)$END"

    checkTextHasError(selected)

    val text = "Map(1 -> 2).find(_._1 == 1).map(_._2)"

    val result = "Map(1 -> 2).get(1)"

    testQuickFix(text, result, hint)
  }

  def test_with_map_as_val(): Unit = {
    val selected =
      s"""val m = Map("k" -> "5", "v" -> "6")
         |m.${START}find(_._1 == "5").map(_._2)$END
         |""".stripMargin
    checkTextHasError(selected)

    val text =
      s"""val m = Map("k" -> "5", "v" -> "6")
         |m.find(_._1 == "5").map(_._2)
         |""".stripMargin

    val result =
      s"""val m = Map("k" -> "5", "v" -> "6")
         |m.get("5")
         |""".stripMargin

    testQuickFix(text, result, hint)
  }
}
