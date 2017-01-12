package org.jetbrains.plugins.scala.codeInspection.collections

import com.intellij.testFramework.EditorTestUtil

/**
  * mattfowler
  * 4/28/16
  */
class FindAndMapToApplyTest extends OperationsOnCollectionInspectionTest {

  import EditorTestUtil.{SELECTION_END_TAG => END, SELECTION_START_TAG => START}

  def test_inline_map() {

    val selected = s"Map().${START}find(_ == 1).map(_._2)$END"

    check(selected)

    val text = "Map().find(_ == 1).map(_._2)"

    val result = "Map()(1)"

    testFix(text, result, hint)
  }

  def test_with_map_as_val() = {

    val selected =
      s"""val m = Map("k" -> "5", "v" -> "6")
          m.${START}find(_ == "5").map(_._2)$END"""
    check(selected)

    val text =
      s"""val m = Map("k" -> "5", "v" -> "6")
          m.find(_ == "5").map(_._2)""".stripMargin

    val result =
      s"""val m = Map("k" -> "5", "v" -> "6")
          m("5")""".stripMargin

    testFix(text, result, hint)

  }

  override val inspectionClass = classOf[FindAndMapToApplyInspection]

  override def hint: String = "Replace find and map with apply"
}
