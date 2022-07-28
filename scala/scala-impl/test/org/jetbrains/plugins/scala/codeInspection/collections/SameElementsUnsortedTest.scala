package org.jetbrains.plugins.scala
package codeInspection
package collections

abstract class CorrespondsUnsortedInspectionTest extends OperationsOnCollectionInspectionTest {

  override val classOfInspection: Class[_ <: OperationOnCollectionInspection] =
    classOf[CorrespondsUnsortedInspection]
}

class SameElementsUnsortedTest extends CorrespondsUnsortedInspectionTest {

  override protected val hint: String =
    ScalaInspectionBundle.message("sameElements.unsorted")

  def testSeqSet(): Unit = {
    checkTextHasError(s"Seq(1) ${START}sameElements$END Set(1)")
  }

  def testSetSet(): Unit = {
    checkTextHasError(s"Set(1) ${START}sameElements$END Set(1)")
  }

  def testSeqMap(): Unit = {
    checkTextHasError(s"Map(1 -> 1) ${START}sameElements$END Seq(1)")
  }

  def testSeqIterable(): Unit = {
    checkTextHasError(s"Seq(1) ${START}sameElements$END Iterable(1)")
  }

  def testSetSortedSet(): Unit = {
    checkTextHasError(s"Set(1).${START}sameElements$END(scala.collection.SortedSet(1))")
  }

  def testSeqSortedSet(): Unit = {
    checkTextHasNoErrors("Seq(1).sameElements(scala.collection.SortedSet(1))")
  }

  def testSeqSortedMap(): Unit = {
    checkTextHasNoErrors("Seq((1, 1)).sameElements(scala.collection.SortedMap(1 -> 1))")
  }

  def testSeqArray(): Unit = {
    checkTextHasNoErrors("Seq(1).sameElements(Array(1))")
  }

  def testArrayArray(): Unit = {
    checkTextHasNoErrors("Array(1).sameElements(Array(1))")
  }

  def testIterators(): Unit = {
    checkTextHasNoErrors("Iterator(1).sameElements(Iterator(1))")
  }
}

class CorrespondsUnsortedTest extends CorrespondsUnsortedInspectionTest {

  override protected val hint: String =
    ScalaInspectionBundle.message("corresponds.unsorted")

  def testCorrespondsSet(): Unit = {
    checkTextHasError(s"Iterator(1).${START}corresponds$END(Set(1))((x, y) => true)")
  }

  def testCorrespondsSortedSet(): Unit = {
    checkTextHasNoErrors("Iterator(1).corresponds(scala.collection.SortedSet(1))((x, y) => true)")
  }

  def testCorrespondsArray(): Unit = {
    checkTextHasNoErrors("Iterator(1).corresponds(Array(1))((x, y) => true)")
  }

  def testSeqCorrespondsSeq(): Unit = {
    checkTextHasNoErrors("Seq(1).corresponds(Seq(1))((x, y) => true)")
  }

  def testSeqCorrespondsSet(): Unit = {
    checkTextHasNoErrors("Seq(1).corresponds(Seq(1))((x, y) => true)")
  }
}

