package org.jetbrains.plugins.scala.lang.psi.irrefutability

import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScPattern
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScMatch}

class IrrefutabilityTest extends ScalaLightCodeInsightFixtureTestAdapter {

  private object MatchWithOneCasePatternMatch {
    def unapply(arg: ScMatch): Option[(ScPattern, ScExpression)] =
      for {
        expr <- arg.expression
        Seq(clause) <- Some(arg.clauses)
        pattern <- clause.pattern
      } yield pattern -> expr
  }

  private def isIrrefutable(@Language("Scala") code: String): Boolean = {
    val testCode =
      s"""
        |{
        |  class Base
        |  class A extends Base
        |  class B extends Base
        |  object A extends A
        |  object B extends B
        |  case class Fun[A, B](a: A, rest: B*)
        |
        |  $code
         }
      """.stripMargin
    val file = configureFromFileText(testCode).asInstanceOf[ScalaFile]
    val root = file.getFirstChild.asInstanceOf[ScalaPsiElement]

    val List((pattern, expr)) = root.findChildrenByType(ScalaElementType.MATCH_STMT) collect {
      case MatchWithOneCasePatternMatch(p, e) => p -> e
    }

    val exprType = expr.`type`().get
    pattern.isIrrefutableFor(Some(exprType))
  }

  def assertIsIrrefutable(@Language("Scala") code: String): Unit = {
    assert(isIrrefutable(code), s"Code is not irrefutable: '$code'")
  }

  def assertIsNotIrrefutable(@Language("Scala") code: String): Unit = {
    assert(!isIrrefutable(code), s"Code is irrefutable: '$code'")

  }


  def testWildcardPattern(): Unit = {
    assertIsIrrefutable("A match { case _ => }")
  }

  def testReferencePattern(): Unit = {
    assertIsIrrefutable("A match { case a => }")
    assertIsIrrefutable("(A, B) match { case a => }")
  }

  def testTypedPattern(): Unit = {
    assertIsIrrefutable("A match { case a: A => }")
    assertIsIrrefutable("A match { case a: Base => }")
    assertIsIrrefutable("(A, B) match { case a: (A, B) => }")
    assertIsIrrefutable("(B, A) match { case a: (Base, Base) => }")

    assertIsNotIrrefutable("B match { case a: A => }")
    assertIsNotIrrefutable("new Base match { case a: A => }")
    assertIsNotIrrefutable("(B, A) match { case a: (A, B) => }")
    assertIsNotIrrefutable("(A, A, A) match { case a: (A, A) => }")
  }

  def testNamingPattern(): Unit = {
    assertIsIrrefutable("A match { case a@_ => }")
    assertIsIrrefutable("(A, B) match { case x@(_, _) => }")
    assertIsIrrefutable("Some(A) match { case x@Some(a: A) => }")

    assertIsNotIrrefutable("(A, B, B) match { case x@(_, _) => }")
    assertIsNotIrrefutable("Some(B) match { case x@Some(a: A) => }")
    assertIsNotIrrefutable("Option(A) match { case x@Some(a: A) => }")
  }

  def testTuplePattern(): Unit = {
    assertIsIrrefutable("(A, B) match { case (a, b) => }")
    assertIsIrrefutable("(A, A -> B) match { case (a, b) => }")
    assertIsIrrefutable("(A, A -> B) match { case (a, (a2, b)) => }")
    assertIsIrrefutable("(A, A -> B) match { case (a:A, (a2:A, b: B)) => }")

    assertIsNotIrrefutable("A match { case (a, b) => }")
    assertIsNotIrrefutable("(A, B, B) match { case (a, b) => }")
    assertIsNotIrrefutable("(B, A) match { case (a: A, b: B) => }")
    assertIsNotIrrefutable("(A, B -> A) match { case (_, (a: A, b: B)) => }")
  }

  def testConstructorPattern(): Unit = {
    assertIsIrrefutable("Some(A) match { case Some(a) => }")
    assertIsIrrefutable("Some(A -> B) match { case Some((a, b)) => }")

    assertIsNotIrrefutable("Option(A) match { case Some(a) => }")
    assertIsNotIrrefutable("Some(A) match { case Some(a: B) => }")
    assertIsNotIrrefutable("A match { case Some(_) => }")
  }

  def testParenthesisedPattern(): Unit = {
    assertIsIrrefutable("A match { case (a) => }")
    assertIsIrrefutable("A match { case ((a)) => }")
    assertIsIrrefutable("A match { case (((_))) => }")
    assertIsIrrefutable("Some(A) match { case (Some(a)) => }")

    assertIsNotIrrefutable("A match { case (a: B) => }")
    assertIsNotIrrefutable("Option(A) match { case (Some(a)) => }")
  }


  def testLiteralPattern(): Unit = {
    assertIsNotIrrefutable("1 match { case 1 => }")
    assertIsNotIrrefutable("\"test\" match { case \"test\" => }")
  }

  def testStableReferencePattern(): Unit = {
    assertIsNotIrrefutable("A match { case `A` => }")
  }

  def testInfixPattern(): Unit = {
    assertIsIrrefutable("::(A, Nil) match { case a :: rest => }")

    assertIsNotIrrefutable("List(A, A) match { case a +: b => }")
  }

  def testCompositePattern(): Unit = {
    assertIsIrrefutable("A match { case _:A | _: B => }")
    assertIsIrrefutable("B match { case _:A | _: B => }")
    assertIsIrrefutable("(A, A) match { case _:(A, B) | _:(B, A) | _:(B, B) | _:(A, A) => }")

    assertIsNotIrrefutable("(A, A) match { case _:(A, B) | _:(B, A) | _:(B, B) => }")
    assertIsNotIrrefutable("A match { case _: B | (a, b)}")
  }

  def testXmlPattern(): Unit = {
    assertIsNotIrrefutable("<b>0</b> match { case <a>x</a> => }")
    assertIsNotIrrefutable("<a>0</a> match { case <a>x</a> => }")
  }

  def testWildcardSeqPattern(): Unit = {
    assertIsIrrefutable("Fun(A, B) match { case Fun(a, _*) => }")
    assertIsIrrefutable("Fun(A, B) match { case Fun(a, b@_*) => }")
    assertIsIrrefutable("Fun(A, B, C) match { case Fun(a, _*) => }")
    assertIsIrrefutable("Fun(A, B, C) match { case Fun(a, b@_*) => }")

    assertIsNotIrrefutable("A match { case (_*) => }")
    assertIsNotIrrefutable("Some(A) match { case Some(_*) => }")
    assertIsNotIrrefutable("Some(A) match { case Some(a@_*) => }")
    assertIsNotIrrefutable("Fun(A, B) match { case Fun(a, b) => }")
    assertIsNotIrrefutable("Fun(A, B) match { case Fun(a) => }")
    assertIsNotIrrefutable("Fun(A, B) match { case Fun(a, b, _*) => }")
    assertIsNotIrrefutable("Fun(A, B) match { case Fun(a, b, c@_*) => }")
  }
}
