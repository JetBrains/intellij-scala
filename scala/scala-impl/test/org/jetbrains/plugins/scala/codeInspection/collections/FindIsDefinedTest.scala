package org.jetbrains.plugins.scala
package codeInspection
package collections

class FindIsDefinedTest extends OperationsOnCollectionInspectionTest {
  override val classOfInspection = classOf[FindEmptyCheckInspection]
  override val hint = ScalaInspectionBundle.message("find.isDefined.hint")

  def testFindIsDefined(): Unit = {
    val selected = s"""val valueIsGoodEnough: (Any) => Boolean = _ => true
                 |Nil$START.find(valueIsGoodEnough).isDefined$END""".stripMargin
    checkTextHasError(selected)
    val text = """val valueIsGoodEnough: (Any) => Boolean = _ => true
                 |Nil.find(valueIsGoodEnough).isDefined""".stripMargin
    val result = """val valueIsGoodEnough: (Any) => Boolean = _ => true
                   |Nil.exists(valueIsGoodEnough)""".stripMargin
    testQuickFix(text, result, hint)
  }

  def testInfix(): Unit = {
    val selected = s"(Nil$START find (_ => true)) isDefined$END"
    checkTextHasError(selected)
    val text = "(Nil find (_ => true)) isDefined"
    val result = "Nil exists (_ => true)"
    testQuickFix(text, result, hint)
  }

  def testNotEqNoneInfix(): Unit = {
    val selected = s"(Nil$START find (_ => true)) != None$END"
    checkTextHasError(selected)
    val text = "(Nil find (_ => true)) != None"
    val result = "Nil exists (_ => true)"
    testQuickFix(text, result, hint)
  }

  def testNotEqNone(): Unit = {
    val selected = s"Nil$START.find(_ => true) != None$END"
    checkTextHasError(selected)
    val text = "Nil.find(_ => true) != None"
    val result = "Nil.exists(_ => true)"
    testQuickFix(text, result, hint)
  }
}

class FindIsEmptyTest extends OperationsOnCollectionInspectionTest {
  override val classOfInspection = classOf[FindEmptyCheckInspection]
  override val hint = ScalaInspectionBundle.message("find.isEmpty.hint")

  def testEqNone(): Unit = {
    val selected = s"Nil$START.find(_ => true) == None$END"
    checkTextHasError(selected)
    val text = "Nil.find(_ => true) == None"
    val result = "!Nil.exists(_ => true)"
    testQuickFix(text, result, hint)
  }

  def testIsEmpty(): Unit = {
    val selected = s"Nil$START.find(_ => true).isEmpty$END"
    checkTextHasError(selected)
    val text = "Nil.find(_ => true).isEmpty"
    val result = "!Nil.exists(_ => true)"
    testQuickFix(text, result, hint)
  }
}