package org.jetbrains.plugins.scala
package codeInspection
package collections

abstract class MapKeysTest extends OperationsOnCollectionInspectionTest {

  override protected val classOfInspection: Class[_ <: OperationOnCollectionInspection] =
    classOf[MapKeysInspection]
}

class ReplaceWithKeysTest extends MapKeysTest {

  override protected val hint: String =
    ScalaInspectionBundle.message("replace.with.keys")

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
    checkTextHasNoErrors("Seq((1, 2)).map(x => x._1)")
  }
}

class ReplaceWithKeySetTest extends MapKeysTest {

  override protected val hint: String =
    ScalaInspectionBundle.message("replace.with.keySet")

  def test(): Unit = {
    checkTextHasError(s"Map(1 -> 2).${START}map(_._1).toSet$END")
    testQuickFix("Map(1 -> 2).map(_._1).toSet", "Map(1 -> 2).keySet", hint)
  }

}

class ReplaceWithKeysIteratorTest extends MapKeysTest {

  override protected val hint: String =
    ScalaInspectionBundle.message("replace.with.keysIterator")

  def test(): Unit = {
    checkTextHasError(s"Map(1 -> 2).${START}map(_._1).toIterator$END")
    testQuickFix("Map(1 -> 2).map(_._1).toIterator", "Map(1 -> 2).keysIterator", hint)
  }
}
