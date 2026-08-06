package org.jetbrains.plugins.scala
package codeInspection
package collections

class EmulateFlattenInspectionTest extends OperationsOnCollectionInspectionTest {

  override protected val classOfInspection: Class[_ <: OperationOnCollectionInspection] =
    classOf[EmulateFlattenInspection]

  override protected val hint: String =
    ScalaInspectionBundle.message("replace.with.flatten")

  def testSuggests1(): Unit = {
    val selected = s"Seq(Seq(1), Seq(2), Seq(2)).${START}flatMap(identity)$END"
    checkTextHasError(selected)
    val text = "Seq(Seq(1), Seq(2), Seq(2)).flatMap(identity)"
    val result = "Seq(Seq(1), Seq(2), Seq(2)).flatten"
    testQuickFix(text, result, hint)
  }

  def testSuggests2(): Unit = {
    val selected = s"Seq(Seq(1, 2, 3), Seq(4, 5), Seq(6, 7)).${START}flatMap(x => identity(x))$END"
    checkTextHasError(selected)
    val text = "Seq(Seq(1, 2, 3), Seq(4, 5), Seq(6, 7)).flatMap(x => identity(x))"
    val result = "Seq(Seq(1, 2, 3), Seq(4, 5), Seq(6, 7)).flatten"
    testQuickFix(text, result, hint)
  }

  def testSuggests3(): Unit = {
    val selected = s"Seq(Seq(3, 1, 4), Seq(1, 5), Seq(9, 2)).${START}flatMap(identity(_))$END"
    checkTextHasError(selected)
    val text = "Seq(Seq(3, 1, 4), Seq(1, 5), Seq(9, 2)).flatMap(identity(_))"
    val result = "Seq(Seq(3, 1, 4), Seq(1, 5), Seq(9, 2)).flatten"
    testQuickFix(text, result, hint)
  }

  def testSuggests4(): Unit = {
    val selected = s"Seq(Seq(2, 7, 1), Seq(8, 2), Seq(8, 1)).${START}flatMap(x => x)$END"
    checkTextHasError(selected)
    val text = "Seq(Seq(2, 7, 1), Seq(8, 2), Seq(8, 1)).flatMap(x => x)"
    val result = "Seq(Seq(2, 7, 1), Seq(8, 2), Seq(8, 1)).flatten"
    testQuickFix(text, result, hint)
  }

  def testSuggests5(): Unit = {
    val selected = s"Iterator(Iterator(1), Iterator(2), Iterator(3)).${START}flatMap(identity)$END"
    checkTextHasError(selected)
    val text = "Iterator(Iterator(1), Iterator(2), Iterator(3)).flatMap(identity)"
    val result = "Iterator(Iterator(1), Iterator(2), Iterator(3)).flatten"
    testQuickFix(text, result, hint)
  }

  def testSuggests6(): Unit = {
    val selected = s"Iterator(Seq(1), Seq(2), Seq(3)).${START}flatMap(identity)$END"
    checkTextHasError(selected)
    val text = "Iterator(Seq(1), Seq(2), Seq(3)).flatMap(identity)"
    val result = "Iterator(Seq(1), Seq(2), Seq(3)).flatten"
    testQuickFix(text, result, hint)
  }

  def testSuggests7(): Unit = {
    val selected = s"Set(Set(1), Set(2), Set(3)).${START}flatMap(identity)$END"
    checkTextHasError(selected)
    val text = "Set(Set(1), Set(2), Set(3)).flatMap(identity)"
    val result = "Set(Set(1), Set(2), Set(3)).flatten"
    testQuickFix(text, result, hint)
  }

  def testSuggests8(): Unit = {
    val selected = s"Set(Set(1), Set(2), Set(3)).${START}flatMap(x => x)$END"
    checkTextHasError(selected)
    val text = "Set(Set(1), Set(2), Set(3)).flatMap(x => x)"
    val result = "Set(Set(1), Set(2), Set(3)).flatten"
    testQuickFix(text, result, hint)
  }

  def testSuggests9(): Unit = {
    val selected = s"Set(Set(1), Set(2), Set(3)).${START}flatMap(identity[Set[Int]])$END"
    checkTextHasError(selected)
    val text = "Set(Set(1), Set(2), Set(3)).flatMap(identity[Set[Int]])"
    val result = "Set(Set(1), Set(2), Set(3)).flatten"
    testQuickFix(text, result, hint)
  }

  def testSuggests10(): Unit = {
    val selected = s"Set(Set(1), Set(2), Set(3)).${START}flatMap(identity _)$END"
    checkTextHasError(selected)
    val text = "Set(Set(1), Set(2), Set(3)).flatMap(identity _)"
    val result = "Set(Set(1), Set(2), Set(3)).flatten"
    testQuickFix(text, result, hint)
  }

  def testSuggestes11(): Unit = {
    val selected = s"Set(Set(1), Set(2), Set(3)) ${START}flatMap identity$END"
    checkTextHasError(selected)
    val text = "Set(Set(1), Set(2), Set(3)) flatMap identity"
    val result = "Set(Set(1), Set(2), Set(3)).flatten"
    testQuickFix(text, result, hint)
  }

  def testSuggestes12(): Unit = {
    val selected = s"val o = Option(Option(1)); o.${START}getOrElse(None)$END"
    checkTextHasError(selected)
    val text = "val o = Option(Option(1)); o.getOrElse(None)"
    val result = "val o = Option(Option(1)); o.flatten"
    testQuickFix(text, result, hint)
  }

  def testSuggestes13(): Unit = {
    val selected = s"Option(Option(1)).${START}getOrElse(None)$END"
    checkTextHasError(selected)
    val text = "Option(Option(1)).getOrElse(None)"
    val result = "Option(Option(1)).flatten"
    testQuickFix(text, result, hint)
  }

  def testSuggestes14(): Unit = {
    val selected = s"val o = Option(Option(1)); o.${START}map(_.get)$END"
    checkTextHasError(selected)
    val text = "val o = Option(Option(1)); o.map(_.get)"
    val result = "val o = Option(Option(1)); o.flatten"
    testQuickFix(text, result, hint)
  }

  def testSuggestes15(): Unit = {
    val selected = s"Option(Option(1)).${START}map(_.get)$END"
    checkTextHasError(selected)
    val text = "Option(Option(1)).map(_.get)"
    val result = "Option(Option(1)).flatten"
    testQuickFix(text, result, hint)
  }

  def testNotSuggests1(): Unit = {
    val text = s"Seq(Seq(1), Seq(2), Seq(3)).flatMap(x => identity(Seq(1, 2, 3)))"
    checkTextHasNoErrors(text)
  }

  def testNotSuggests2(): Unit = {
    val text = s"Seq(Seq(9), Seq(8, 1), Seq(5, 9, 9)).flatMap(_.map(_ * 2))"
    checkTextHasNoErrors(text)
  }

  def testNotSuggests3(): Unit = {
    val text = s"Set(Set(1), Set(2), Set(3)).flatMap { x => println(x); x }"
    checkTextHasNoErrors(text)
  }

  def testNotSuggests4(): Unit = {
    val text = s"List(List(1), List(2), List(3)).flatMap(1 :: _ )"
    checkTextHasNoErrors(text)
  }

  def testNotSuggests5(): Unit = {
    val text = s"Option(Option(1)).getOrElse(Option(2))"
    checkTextHasNoErrors(text)
  }

  def testNotSuggests6(): Unit = {
    val text = s"Option(Option(1), 2).getOrElse(None)"
    checkTextHasNoErrors(text)
  }

  def testNotSuggests7(): Unit = {
    val text = s"Option(List(1)).getOrElse(None)"
    checkTextHasNoErrors(text)
  }
}
