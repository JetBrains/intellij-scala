package org.jetbrains.plugins.scala.codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.InspectionBundle

/**
  * @author Lukasz Piepiora
  */
class EmulateFlattenTest extends OperationsOnCollectionInspectionTest {

  override val inspectionClass: Class[_ <: OperationOnCollectionInspection] =
    classOf[EmulateFlattenInspection]

  override def hint: String = InspectionBundle.message("replace.with.flatten")

  def testSuggests1(): Unit = {
    val selected = s"Seq(Seq(1), Seq(2), Seq(2)).${START}flatMap(identity)$END"
    checkTextHasError(selected, hint, inspectionClass)
    val text = "Seq(Seq(1), Seq(2), Seq(2)).flatMap(identity)"
    val result = "Seq(Seq(1), Seq(2), Seq(2)).flatten"
    testFix(text, result, hint)
  }

  def testSuggests2(): Unit = {
    val selected = s"Seq(Seq(1, 2, 3), Seq(4, 5), Seq(6, 7)).${START}flatMap(x => identity(x))$END"
    checkTextHasError(selected, hint, inspectionClass)
    val text = "Seq(Seq(1, 2, 3), Seq(4, 5), Seq(6, 7)).flatMap(x => identity(x))"
    val result = "Seq(Seq(1, 2, 3), Seq(4, 5), Seq(6, 7)).flatten"
    testFix(text, result, hint)
  }

  def testSuggests3(): Unit = {
    val selected = s"Seq(Seq(3, 1, 4), Seq(1, 5), Seq(9, 2)).${START}flatMap(identity(_))$END"
    checkTextHasError(selected, hint, inspectionClass)
    val text = "Seq(Seq(3, 1, 4), Seq(1, 5), Seq(9, 2)).flatMap(identity(_))"
    val result = "Seq(Seq(3, 1, 4), Seq(1, 5), Seq(9, 2)).flatten"
    testFix(text, result, hint)
  }

  def testSuggests4(): Unit = {
    val selected = s"Seq(Seq(2, 7, 1), Seq(8, 2), Seq(8, 1)).${START}flatMap(x => x)$END"
    checkTextHasError(selected, hint, inspectionClass)
    val text = "Seq(Seq(2, 7, 1), Seq(8, 2), Seq(8, 1)).flatMap(x => x)"
    val result = "Seq(Seq(2, 7, 1), Seq(8, 2), Seq(8, 1)).flatten"
    testFix(text, result, hint)
  }

  def testSuggests5(): Unit = {
    val selected = s"Iterator(Iterator(1), Iterator(2), Iterator(3)).${START}flatMap(identity)$END"
    checkTextHasError(selected, hint, inspectionClass)
    val text = "Iterator(Iterator(1), Iterator(2), Iterator(3)).flatMap(identity)"
    val result = "Iterator(Iterator(1), Iterator(2), Iterator(3)).flatten"
    testFix(text, result, hint)
  }

  def testSuggests6(): Unit = {
    val selected = s"Iterator(Seq(1), Seq(2), Seq(3)).${START}flatMap(identity)$END"
    checkTextHasError(selected, hint, inspectionClass)
    val text = "Iterator(Seq(1), Seq(2), Seq(3)).flatMap(identity)"
    val result = "Iterator(Seq(1), Seq(2), Seq(3)).flatten"
    testFix(text, result, hint)
  }

  def testSuggests7(): Unit = {
    val selected = s"Set(Set(1), Set(2), Set(3)).${START}flatMap(identity)$END"
    checkTextHasError(selected, hint, inspectionClass)
    val text = "Set(Set(1), Set(2), Set(3)).flatMap(identity)"
    val result = "Set(Set(1), Set(2), Set(3)).flatten"
    testFix(text, result, hint)
  }

  def testSuggests8(): Unit = {
    val selected = s"Set(Set(1), Set(2), Set(3)).${START}flatMap(x => x)$END"
    checkTextHasError(selected, hint, inspectionClass)
    val text = "Set(Set(1), Set(2), Set(3)).flatMap(x => x)"
    val result = "Set(Set(1), Set(2), Set(3)).flatten"
    testFix(text, result, hint)
  }

  def testSuggests9(): Unit = {
    val selected = s"Set(Set(1), Set(2), Set(3)).${START}flatMap(identity[Set[Int]])$END"
    checkTextHasError(selected, hint, inspectionClass)
    val text = "Set(Set(1), Set(2), Set(3)).flatMap(identity[Set[Int]])"
    val result = "Set(Set(1), Set(2), Set(3)).flatten"
    testFix(text, result, hint)
  }

  def testSuggests10(): Unit = {
    val selected = s"Set(Set(1), Set(2), Set(3)).${START}flatMap(identity _)$END"
    checkTextHasError(selected, hint, inspectionClass)
    val text = "Set(Set(1), Set(2), Set(3)).flatMap(identity _)"
    val result = "Set(Set(1), Set(2), Set(3)).flatten"
    testFix(text, result, hint)
  }

  def testSuggestes11(): Unit = {
    val selected = s"Set(Set(1), Set(2), Set(3)) ${START}flatMap identity$END"
    checkTextHasError(selected, hint, inspectionClass)
    val text = "Set(Set(1), Set(2), Set(3)) flatMap identity"
    val result = "Set(Set(1), Set(2), Set(3)).flatten"
    testFix(text, result, hint)
  }

  def testNotSuggests1(): Unit = {
    val text = s"Seq(Seq(1), Seq(2), Seq(3)).flatMap(x => identity(Seq(1, 2, 3)))"
    checkTextHasNoErrors(text, hint, inspectionClass)
  }

  def testNotSuggests2(): Unit = {
    val text = s"Seq(Seq(9), Seq(8, 1), Seq(5, 9, 9)).flatMap(_.map(_ * 2))"
    checkTextHasNoErrors(text, hint, inspectionClass)
  }

  def testNotSuggests3(): Unit = {
    val text = s"Set(Set(1), Set(2), Set(3)).flatMap { x => println(x); x }"
    checkTextHasNoErrors(text, hint, inspectionClass)
  }

  def testNotSuggests4(): Unit = {
    val text = s"List(List(1), List(2), List(3)).flatMap(1 :: _ )"
    checkTextHasNoErrors(text, hint, inspectionClass)
  }

}
