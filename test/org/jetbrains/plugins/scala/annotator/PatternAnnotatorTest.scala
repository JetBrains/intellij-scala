package org.jetbrains.plugins.scala
package annotator

import org.jetbrains.plugins.scala.base.ScalaLightPlatformCodeInsightTestCaseAdapter
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScPattern
import org.junit.Assert.assertEquals

/**
 * Nikolay.Tropin
 * 2014-04-03
 */
class PatternAnnotatorTest extends ScalaLightPlatformCodeInsightTestCaseAdapter {

  private def fruitless(exprType: String, patType: String) = ScalaBundle.message("fruitless.type.test", exprType, patType)
  private def incompatible(exprType: String, patType: String) = ScalaBundle.message("scrutinee.incompatible.pattern.type", exprType, patType)
  private def cannotBeUsed(typeText: String) = s"type $typeText cannot be used in a type pattern or isInstanceOf test"

  private def collectAnnotatorMessages(text: String) = {
    configureFromFileTextAdapter("dummy.scala", text)
    val mock = new AnnotatorHolderMock
    val annotator = new PatternAnnotator {}
    val patterns = getFileAdapter.depthFirst.collect {
      case p: ScPattern => p
    }
    patterns.foreach(p => annotator.annotatePattern(p, mock, true))
    mock.annotations
  }

  private def emptyMessages(text: String) {
    assertEquals(Nil, collectAnnotatorMessages(text))
  }

  private def checkWarning(text: String, element: String, expectedMsg: String) {
    collectAnnotatorMessages(text).foreach {
      case Warning(elem, msg) =>
        assertEquals(element, elem)
        assertEquals(expectedMsg, msg)
      case _ => false
    }
  }

  private def checkError(text: String, element: String, expectedMsg: String) {
    collectAnnotatorMessages(text).foreach {
      case Error(elem, msg) =>
        assertEquals(element, elem)
        assertEquals(expectedMsg, msg)
      case _ => false
    }
  }

  def testConstructorPatternFruitless() = {
    checkWarning("val Some(x) = None", "Some(x)", fruitless("None.type", "Some[A]"))
    checkWarning("val Vector(a) = Nil", "Vector(a)", fruitless("Nil.type", "Vector[A]"))
    checkWarning("val Vector(a) = List(1)", "Vector(a)", fruitless("List[Int]", "Vector[A]"))
    checkWarning("val List(seq: Seq[Int]) = List(List(\"\"))", "seq: Seq[Int]",
      fruitless("List[String]", "Seq[Int]") + ScalaBundle.message("erasure.warning"))
    emptyMessages("val Seq(a) = List(1)")
    emptyMessages("val Vector(a) = Seq(1)")
  }

  def testStableIdPattern() {
    checkWarning("val xs = List(\"\"); val a :: `xs` = 1 :: List(1)", "`xs`", fruitless("List[Int]", "List[String]"))
  }

  def testLiteralPattern() {
    checkWarning("val \"a\" :: xs = 1 :: Nil", "\"a\"", fruitless("Int", "String"))
  }

  def testNullLiteralPattern() {
    checkWarning("val null :: xs = 1 :: Nil", "null", fruitless("Int", "AnyRef"))
    emptyMessages("val null :: xs = \"1\" :: Nil")
  }

  def testTuplePattern() = {
    checkWarning("val (x, y) = (1, 2, 3)", "(x, y)", fruitless("(Int, Int, Int)", "(Int, Int)"))
    checkError("val (x: String, y) = (1, 2)", "x: String", incompatible("Int", "String"))
    emptyMessages("def a: AnyRef = null; val (x, y) = a")
  }

  def testIncompatible() {
    checkError("val Some(x: Int) = \"\"", "x: Int", incompatible("String", "Int"))
    checkError("val (x: Int) :: xs = List(\"1\", \"2\")", "x: Int", incompatible("String", "Int"))
  }

  def testCannotBeUsed() {
    checkError("""x match {
                  |  case _: AnyVal =>
                  |}""".stripMargin.replace("\r", ""), "_: AnyVal", cannotBeUsed("AnyVal"))
    checkError("""x match {
                  |  case n: Null =>
                  |}""".stripMargin.replace("\r", ""), "n: Null", cannotBeUsed("Null"))
    checkError("""x match {
                  |  case n: Nothing =>
                  |}""".stripMargin.replace("\r", ""), "n: Nothing", cannotBeUsed("Nothing"))
  }

  def testUncheckedRefinement() {
    checkWarning("val Some(x: AnyRef{def foo(i: Int): Int}) = Some(new AnyRef())", "AnyRef{def foo(i: Int): Int}",
      ScalaBundle.message("pattern.on.refinement.unchecked"))
  }
}
