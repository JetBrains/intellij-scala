package org.jetbrains.plugins.scala
package codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.InspectionBundle

/**
 * Nikolay.Tropin
 * 5/30/13
 */
class FindIsDefinedTest extends OperationsOnCollectionInspectionTest {
  override val inspectionClass = classOf[FindEmptyCheckInspection]
  override val hint = InspectionBundle.message("find.isDefined.hint")

  def testFindIsDefined() {
    val selected = s"""val valueIsGoodEnough: (Any) => Boolean = _ => true
                 |Nil$START.find(valueIsGoodEnough).isDefined$END""".stripMargin
    check(selected)
    val text = """val valueIsGoodEnough: (Any) => Boolean = _ => true
                 |Nil.find(valueIsGoodEnough).isDefined""".stripMargin
    val result = """val valueIsGoodEnough: (Any) => Boolean = _ => true
                   |Nil.exists(valueIsGoodEnough)""".stripMargin
    testFix(text, result, hint)
  }

  def testInfix() {
    val selected = s"(Nil$START find (_ => true)) isDefined$END"
    check(selected)
    val text = "(Nil find (_ => true)) isDefined"
    val result = "Nil exists (_ => true)"
    testFix(text, result, hint)
  }

  def testNotEqNoneInfix() {
    val selected = s"(Nil$START find (_ => true)) != None$END"
    check(selected)
    val text = "(Nil find (_ => true)) != None"
    val result = "Nil exists (_ => true)"
    testFix(text, result, hint)
  }

  def testNotEqNone() {
    val selected = s"Nil$START.find(_ => true) != None$END"
    check(selected)
    val text = "Nil.find(_ => true) != None"
    val result = "Nil.exists(_ => true)"
    testFix(text, result, hint)
  }
}

class FindIsEmptyTest extends OperationsOnCollectionInspectionTest {
  override val inspectionClass = classOf[FindEmptyCheckInspection]
  override val hint = InspectionBundle.message("find.isEmpty.hint")

  def testEqNone() {
    val selected = s"Nil$START.find(_ => true) == None$END"
    check(selected)
    val text = "Nil.find(_ => true) == None"
    val result = "!Nil.exists(_ => true)"
    testFix(text, result, hint)
  }

  def testIsEmpty() {
    val selected = s"Nil$START.find(_ => true).isEmpty$END"
    check(selected)
    val text = "Nil.find(_ => true).isEmpty"
    val result = "!Nil.exists(_ => true)"
    testFix(text, result, hint)
  }
}