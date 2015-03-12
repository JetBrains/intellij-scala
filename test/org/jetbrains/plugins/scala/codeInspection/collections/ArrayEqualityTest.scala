package org.jetbrains.plugins.scala.codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.InspectionBundle

/**
 * @author Nikolay.Tropin
 */
class ArrayEqualityTest extends OperationsOnCollectionInspectionTest {
  override val inspectionClass: Class[_ <: OperationOnCollectionInspection] = classOf[ArrayEqualityInspection]

  override def hint: String = InspectionBundle.message("replace.equals.with.sameElements.for.array")

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
}
