package org.jetbrains.plugins.scala.codeInspection.collections

import com.intellij.testFramework.EditorTestUtil.{SELECTION_END_TAG => END, SELECTION_START_TAG => START}
import org.jetbrains.plugins.scala.codeInspection.InspectionBundle

/**
  * @author t-kameyama
  */
class MapLiftTest extends OperationsOnCollectionInspectionTest {

  override protected val classOfInspection: Class[_ <: OperationOnCollectionInspection] =
    classOf[MapLiftInspection]

  override protected val hint: String =
    InspectionBundle.message("replace.with.get")

  def test1(): Unit = {
    doTest(
      s"Map(1 -> 2).${START}lift(1)$END",
      "Map(1 -> 2).lift(1)",
      "Map(1 -> 2).get(1)"
    )
  }

  def test2(): Unit = {
    doTest(
      s"Map(1 -> 2) ${START}lift 1$END",
      "Map(1 -> 2) lift 1",
      "Map(1 -> 2) get 1"
    )
  }

}
