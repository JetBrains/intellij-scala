package org.jetbrains.plugins.scala.codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.InspectionBundle

/**
 * @author Nikolay.Tropin
 */
class MapKeysTest extends OperationsOnCollectionInspectionTest{
  override val inspectionClass: Class[_ <: OperationOnCollectionInspection] = classOf[MapKeysInspection]

  override val hint: String = InspectionBundle.message("replace.with.keys")
  val setHint = InspectionBundle.message("replace.with.keySet")
  val iteratorHint = InspectionBundle.message("replace.with.keysIterator")

  def test1(): Unit = {
    doTest(
      s"Map(1 -> 2) ${START}map (x => x._1)$END",
      "Map(1 -> 2) map (x => x._1)",
      "Map(1 -> 2).keys"
    )
  }

  def test2(): Unit = {
    doTest(
      s"Map(1 -> 2).${START}map(_._1)$END",
      "Map(1 -> 2).map(_._1)",
      "Map(1 -> 2).keys"
    )
  }

  def test3(): Unit = {
    checkTextHasError(s"Map(1 -> 2).${START}map(_._1).toSet$END", setHint, inspectionClass)
    testFix("Map(1 -> 2).map(_._1).toSet", "Map(1 -> 2).keySet", setHint)
  }

  def test4(): Unit = {
    checkTextHasError(s"Map(1 -> 2).${START}map(_._1).toIterator$END", iteratorHint, inspectionClass)
    testFix("Map(1 -> 2).map(_._1).toIterator", "Map(1 -> 2).keysIterator", iteratorHint)
  }

  def test5(): Unit = {
    checkTextHasNoErrors("Seq((1, 2)).map(x => x._1)")
  }
}

class MapValuesTest extends OperationsOnCollectionInspectionTest{
  override val inspectionClass: Class[_ <: OperationOnCollectionInspection] = classOf[MapValuesInspection]

  override val hint: String = InspectionBundle.message("replace.with.values")
  val iteratorHint = InspectionBundle.message("replace.with.valuesIterator")

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
    checkTextHasError(s"Map(1 -> 2).${START}map(_._2).toIterator$END", iteratorHint, inspectionClass)
    testFix("Map(1 -> 2).map(_._2).toIterator", "Map(1 -> 2).valuesIterator", iteratorHint)
  }

  def test4(): Unit = {
    checkTextHasNoErrors("Seq((1, 2)).map(x => x._2)")
  }
}
