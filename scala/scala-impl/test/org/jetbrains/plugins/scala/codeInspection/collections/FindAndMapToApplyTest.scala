package org.jetbrains.plugins.scala
package codeInspection
package collections

import com.intellij.testFramework.EditorTestUtil

/**
  * mattfowler
  * 4/28/16
  */
class FindAndMapToApplyTest extends OperationsOnCollectionInspectionTest {

  override protected val classOfInspection: Class[FindAndMapToApplyInspection] =
    classOf[FindAndMapToApplyInspection]

  override protected val hint: String =
    "Replace find and map with apply"

  def test_inline_map(): Unit = {
    val selected = s"Map().${START}find(_ == 1).map(_._2)$END"

    checkTextHasError(selected)

    val text = "Map().find(_ == 1).map(_._2)"

    val result = "Map()(1)"

    testQuickFix(text, result, hint)
  }

  def test_with_map_as_val(): Unit = {
    val selected =
      s"""val m = Map("k" -> "5", "v" -> "6")
          m.${START}find(_ == "5").map(_._2)$END"""
    checkTextHasError(selected)

    val text =
      s"""val m = Map("k" -> "5", "v" -> "6")
          m.find(_ == "5").map(_._2)""".stripMargin

    val result =
      s"""val m = Map("k" -> "5", "v" -> "6")
          m("5")""".stripMargin

    testQuickFix(text, result, hint)
  }
}
