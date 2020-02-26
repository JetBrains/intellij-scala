package org.jetbrains.plugins.scala
package codeInspection
package collections


/**
  * @author t-kameyama
  */
class MapLiftTest extends OperationsOnCollectionInspectionTest {

  override protected val classOfInspection: Class[_ <: OperationOnCollectionInspection] =
    classOf[MapLiftInspection]

  override protected val hint: String =
    ScalaInspectionBundle.message("replace.with.get")

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
