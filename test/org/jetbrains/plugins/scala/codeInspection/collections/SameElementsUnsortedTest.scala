package org.jetbrains.plugins.scala.codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.InspectionBundle

/**
 * @author Nikolay.Tropin
 */
class SameElementsUnsortedTest extends OperationsOnCollectionInspectionTest {
  override val inspectionClass: Class[_ <: OperationOnCollectionInspection] = classOf[CorrespondsUnsortedInspection]

  override def hint: String = InspectionBundle.message("sameElements.unsorted")

  def testSeqSet(): Unit = {
    check(s"Seq(1) ${START}sameElements$END Set(1)")
  }

  def testSetSet(): Unit = {
    check(s"Set(1) ${START}sameElements$END Set(1)")
  }

  def testSeqMap(): Unit = {
    check(s"Map(1) ${START}sameElements$END Seq(1)")
  }

  def testSeqIterable(): Unit = {
    check(s"Seq(1) ${START}sameElements$END Iterable(1)")
  }

  def testSetSortedSet(): Unit = {
    check(s"Set(1).${START}sameElements$END(scala.collection.SortedSet(1))")
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

class CorrespondsUnsortedTest extends OperationsOnCollectionInspectionTest {
  override val inspectionClass: Class[_ <: OperationOnCollectionInspection] = classOf[CorrespondsUnsortedInspection]

  override def hint: String = InspectionBundle.message("corresponds.unsorted")

  def testCorrespondsSet(): Unit = {
    check(s"Iterator(1).${START}corresponds$END(Set(1))((x, y) => true)")
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

