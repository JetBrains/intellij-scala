package org.jetbrains.plugins.scala
package codeInspection
package collections

abstract class MapValuesTest extends OperationsOnCollectionInspectionTest {

  override protected val classOfInspection: Class[_ <: OperationOnCollectionInspection] =
    classOf[MapValuesInspection]
}

class ReplaceWithValuesTest extends MapValuesTest {

  override protected val hint: String =
    ScalaInspectionBundle.message("replace.with.values")

  def test1(): Unit = {
    doTest(
      s"Map(1 -> 2) ${START}map (x => x._2)$END",
      "Map(1 -> 2) map (x => x._2)",
      "Map(1 -> 2).values"
    )
  }

  def test2(): Unit = {
    doTest(
      s"Map(1 -> 2).${START}map(_._2)$END",
      "Map(1 -> 2).map(_._2)",
      "Map(1 -> 2).values"
    )
  }

  def test3(): Unit = {
    checkTextHasNoErrors("Seq((1, 2)).map(x => x._2)")
  }
}

class ReplaceWithValuesIteratorTest extends MapValuesTest {

  override protected val hint: String =
    ScalaInspectionBundle.message("replace.with.valuesIterator")

  def test(): Unit = {
    checkTextHasError(s"Map(1 -> 2).${START}map(_._2).toIterator$END")
    testQuickFix("Map(1 -> 2).map(_._2).toIterator", "Map(1 -> 2).valuesIterator", hint)
  }
}
