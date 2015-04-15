package org.jetbrains.plugins.scala.codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.InspectionBundle

/**
 * @author Nikolay.Tropin
 */
class EqualityToSameElementsTest extends OperationsOnCollectionInspectionTest {
  override val inspectionClass: Class[_ <: OperationOnCollectionInspection] = classOf[EqualityToSameElementsInspection]

  override def hint: String = InspectionBundle.message("replace.equals.with.sameElements")

  def testArraysEquals(): Unit = {
    doTest(
      s"Array(1) $START==$END Array(1)",
      "Array(1) == Array(1)",
      "Array(1) sameElements Array(1)"
    )
  }

  def testArraysEquals2(): Unit = {
    doTest(
      s"Array(1).${START}equals$END(Array(1))",
      "Array(1).equals(Array(1))",
      "Array(1).sameElements(Array(1))"
    )
  }

  def testArraysSeqEquals(): Unit = {
    doTest(
      s"Array(1).${START}equals$END(Seq(1))",
      "Array(1).equals(Seq(1))",
      "Array(1).sameElements(Seq(1))"
    )
  }

  def testSeqArrayEquals(): Unit = {
    doTest(
      s"Seq(1) $START==$END Array(1)",
      "Seq(1) == Array(1)",
      "Seq(1) sameElements Array(1)"
    )
  }

  def testSeqSeqEquals(): Unit = {
    checkTextHasNoErrors("Seq(1) == Seq(1)")
  }

  def testArraysNotEquals(): Unit = {
    doTest(
      s"Array(1) $START!=$END Array(1)",
      "Array(1) != Array(1)",
      "!(Array(1) sameElements Array(1))"
    )
  }

  def testIteratorsEquals(): Unit = {
    doTest(
      s"Iterator(1) $START==$END Iterator(1)",
      "Iterator(1) == Iterator(1)",
      "Iterator(1) sameElements Iterator(1)"
    )
  }

  def testIteratorsNotEquals(): Unit = {
    doTest(
      s"Iterator(1) $START!=$END Iterator(1)",
      "Iterator(1) != Iterator(1)",
      "!(Iterator(1) sameElements Iterator(1))"
    )
  }

}
