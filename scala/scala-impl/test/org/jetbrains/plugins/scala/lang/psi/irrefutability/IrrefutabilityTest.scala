package org.jetbrains.plugins.scala.lang.psi.irrefutability

import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.base.libraryLoaders.{LibraryLoader, ScalaStandardLibraryLoaders}
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaFile, ScalaPsiElement}
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScPattern
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScMatch}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScPatternDefinition

abstract class IrrefutabilityTestBase extends ScalaLightCodeInsightFixtureTestCase {
  private object MatchWithOneCasePatternMatch {
    def unapply(arg: ScMatch): Option[(ScPattern, ScExpression)] =
      for {
        expr <- arg.expression
        Seq(clause) <- Some(arg.clauses)
        pattern <- clause.pattern
      } yield pattern -> expr
  }

  private def isIrrefutable(@Language("Scala") code: String, deep: Boolean): Boolean = {
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
         |}
         |""".stripMargin.trim
    val file = configureFromFileText(testCode).asInstanceOf[ScalaFile]
    val root = file.getFirstChild.asInstanceOf[ScalaPsiElement]

    val byMatch = root.findChildrenByType(ScalaElementType.MATCH_STMT) collect {
      case MatchWithOneCasePatternMatch(p, e) => p -> e
    }

    val byVal = root
      .findChildrenByType(ScalaElementType.PATTERN_DEFINITION).flatMap {
        case p: ScPatternDefinition if p.annotations.exists(_.textMatches("@testval")) => p.pList.patterns.map(_ -> p.expr.get)
        case _ => Nil
      }

    val foundPatterns = if (byMatch.nonEmpty) byMatch else byVal
    assert(foundPatterns.size == 1, s"found not exactly one pattern, patterns: $foundPatterns")

    val List((pattern, expr)) = foundPatterns

    val exprType = expr.`type`().get
    pattern.isIrrefutableFor(exprType, deep)
  }

  def assertIsIrrefutable(code: String): Unit = {
    assert(isIrrefutable(code, deep = true), s"Code is not irrefutable: '$code'")
    assert(isIrrefutable(code, deep = false), s"Expected code to be irrefutable for shallow as well: '$code'")
  }

  def assertIsNotIrrefutable(code: String, butIsForShallow: Boolean = false): Unit = {
    assert(!isIrrefutable(code, deep = true), s"Code is irrefutable: '$code'")
    if (butIsForShallow) {
      assert(isIrrefutable(code, deep = false), s"Code is correctly not irrefutable for deep and incorrectly also for shallow: '$code'")
    } else {
      assert(!isIrrefutable(code, deep = false), s"Code is correctly not irrefutable for deep, but incorrectly for shallow: '$code'")
    }
  }

  def testNothingIsAlwaysIrrefutable(): Unit = {
    assertIsIrrefutable("??? match { case _ => }")
    assertIsIrrefutable("??? match { case a => }")
    assertIsIrrefutable("??? match { case A => }")
    assertIsIrrefutable("??? match { case (1, _) => }")
    assertIsIrrefutable("@testval val 1 = ???")
    assertIsIrrefutable("??? match { case a: A => }")
    assertIsIrrefutable("??? match { case a@(_) => }")
    assertIsIrrefutable("??? match { case Some(a) => }")
    assertIsIrrefutable("??? match { case (_) => }")
    assertIsIrrefutable("??? match { case 1 => }")
    assertIsIrrefutable("??? match { case _ :: _ => }")
    assertIsIrrefutable("??? match { case _: Int | _: String => }")
    assertIsIrrefutable("??? match { case Fun(_*) => }")
  }

  def testWildcardPattern(): Unit = {
    assertIsIrrefutable("A match { case _ => }")
  }

  def testReferencePattern(): Unit = {
    assertIsIrrefutable("A match { case a => }")
    assertIsIrrefutable("(A, B) match { case a => }")
  }

  def testStableReferencePattern(): Unit = {
    // TODO: fix if someone complains
    //assertIsIrrefutable(
    //  """
    //    |object Test
    //    |Test match { case Test => }
    //    |""".stripMargin
    //)
//
    //assertIsIrrefutable(
    //  """
    //    |val x: Int = ???
    //    |x match { case `x` => }
    //    |""".stripMargin
    //)

    //assertIsNotIrrefutable(
    //  """
    //    |val x: Any = 1
    //    |@test val (`x`) = (x: Any)
    //    |""".stripMargin
    //)
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

    assertIsNotIrrefutable("(A, B, B) match { case x@(_, _) => }", butIsForShallow = true)
    assertIsNotIrrefutable("Some(B) match { case x@Some(a: A) => }", butIsForShallow = true)
    assertIsNotIrrefutable("Option(A) match { case x@Some(a: A) => }", butIsForShallow = true)
  }

  def testTuplePattern(): Unit = {
    assertIsIrrefutable("(A, B) match { case (a, b) => }")
    assertIsIrrefutable("(A, A -> B) match { case (a, b) => }")
    assertIsIrrefutable("(A, A -> B) match { case (a, (a2, b)) => }")
    assertIsIrrefutable("(A, A -> B) match { case (a:A, (a2:A, b: B)) => }")

    assertIsNotIrrefutable("A match { case (a, b) => }")
    assertIsNotIrrefutable("(A, B, B) match { case (a, b) => }")
    assertIsNotIrrefutable("(B, A) match { case (a: A, b: B) => }", butIsForShallow = true)
    assertIsNotIrrefutable("(A, B -> A) match { case (_, (a: A, b: B)) => }", butIsForShallow = true)
  }

  def testConstructorPattern(): Unit = {
    assertIsIrrefutable("Some(A) match { case Some(a) => }")
    assertIsIrrefutable("Some(A -> B) match { case Some((a, b)) => }")

    assertIsNotIrrefutable("Option(A) match { case Some(a) => }")
    assertIsNotIrrefutable("Some(A) match { case Some(a: B) => }", butIsForShallow = true)
    assertIsNotIrrefutable("A match { case Some(_) => }")
  }

  def testParenthesisedPattern(): Unit = {
    assertIsIrrefutable("A match { case (a) => }")
    assertIsIrrefutable("A match { case ((a)) => }")
    assertIsIrrefutable("A match { case (((_))) => }")
    assertIsIrrefutable("Some(A) match { case (Some(a)) => }")

    assertIsNotIrrefutable("A match { case (a: B) => }", butIsForShallow = true)
    assertIsNotIrrefutable("Option(A) match { case (Some(a)) => }", butIsForShallow = true)
  }

  def testLiteralPattern(): Unit = {
    // todo: make these work if someone complains
    //assertIsIrrefutable("val 1 = 1")
    //assertIsIrrefutable("\"test\" match { case \"test\" => }")

    assertIsNotIrrefutable("1 match { case 2 => }")
    assertIsNotIrrefutable("(1: Int) match { case n@(2) => }", butIsForShallow = true)
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

  def testBooleanUnapply(): Unit = {
    val common =
      """
        |object Bool {
        |  def unapply(x: Int): Boolean = true
        |}
        |object True {
        |  def unapply(x: Int): true = true
        |}
        |object False {
        |  def unapply(x: Int): false = false
        |}
        |""".stripMargin

    assertIsNotIrrefutable(s"$common 1 match { case Bool() => }")
    assertIsIrrefutable(s"$common 1 match { case True() => }")
    assertIsNotIrrefutable(s"$common 1 match { case False() => }")
  }

  def testUnapplyOption(): Unit = {
    val common =
      """
        |object Opt {
        |  def unapply(i: Int): Option[Int] = None
        |}
        |
        |object Som {
        |  def unapply(i: Int): Some[Int] = Some(3)
        |}
        |
        |""".stripMargin

    assertIsIrrefutable(s"$common 1 match { case Som(a) => }")
    assertIsNotIrrefutable(s"$common 1 match { case Som(1) => }", butIsForShallow = true)

    assertIsNotIrrefutable(s"$common 1 match { case Opt(a) => }")
  }

  def testUnapplyCustom(): Unit = {
    def common(ret: String) =
      s"""
         |class Empty {
         |  def get: Int = 0
         |  def isEmpty: $ret = ???
         |}
         |
         |object Extractor {
         |  def unapply(a: Int): Empty = new Empty
         |}
         |
         |""".stripMargin

    assertIsIrrefutable(s"${common("false")} 1 match { case Extractor(a) => }")
    assertIsNotIrrefutable(s"${common("false")} 1 match { case Extractor(2) => }", butIsForShallow = true)

    assertIsNotIrrefutable(s"${common("true")} 1 match { case Extractor(a) => }")
    assertIsNotIrrefutable(s"${common("Boolean")} 1 match { case Extractor(a) => }")
  }
}

class IrrefutabilityTest_Scala2 extends IrrefutabilityTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version <= ScalaVersion.Latest.Scala_2

  // `scala.xml` is a separate module since Scala 2.11
  override protected def additionalLibraries: Seq[LibraryLoader] = ScalaStandardLibraryLoaders.scalaXmlLoaders

  def testXmlPattern(): Unit = {
    assertIsNotIrrefutable("<b>0</b> match { case <a>x</a> => }")
    assertIsNotIrrefutable("<a>0</a> match { case <a>x</a> => }")

    // check nothing
    assertIsIrrefutable("??? match { case <a>x</a> => }")
  }
}

class IrrefutabilityTest_Scala3 extends IrrefutabilityTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version >= ScalaVersion.Latest.Scala_3_6

  def testNamedTuplePattern(): Unit = {
    assertIsIrrefutable("(x = A, y = B) match { case (x = a, y = b) => }")
    assertIsIrrefutable("(x = 1) match { case (x = _) => }")
    assertIsIrrefutable("(x = 1, y = 2) match { case (x = _) => }")
    assertIsIrrefutable("(x = 1, y = 2) match { case (y = _) => }")
    assertIsIrrefutable("(x = 1, y = 2) match { case (y = _, x = _) => }")

    assertIsNotIrrefutable("(x = 1) match { case (y = _) => }")
    assertIsNotIrrefutable("(x = 1) match { case (x = 2) => }", butIsForShallow = true)
    assertIsNotIrrefutable("(x = 1, y = 2) match { case (y = _, v = _) => }")

    // check nothing
    assertIsIrrefutable("??? match { case (x = 1, y = 2) => }")
  }

  def testNamedTuplePattern2(): Unit = {
    assertIsIrrefutable("(a = A, b = B) match { case (a = a, b = b) => }")
    assertIsIrrefutable("(a = A, b = A -> B) match { case (a = a, b = b) => }")
    assertIsIrrefutable("(a = A, b = A -> B) match { case (a = a, b = (a2, b)) => }")
    assertIsIrrefutable("(a = A, b = A -> B) match { case (a = a:A, b = (a2:A, b: B)) => }")
    assertIsIrrefutable("(a = A, b = B, c = B) match { case (a = a, b = b) => }")

    assertIsNotIrrefutable("A match { case (a, b) => }")
    assertIsNotIrrefutable("(a = B, b = A) match { case (a = a: A, b = b: B) => }", butIsForShallow = true)
    assertIsNotIrrefutable("(a = A, b = B) match { case (b = b, a = A) => }", butIsForShallow = true)
    assertIsNotIrrefutable("(a = A, b = B -> A) match { case (a = _, b = (a: A, b: B)) => }", butIsForShallow = true)
    assertIsNotIrrefutable("(A, B) match { case (b = _, a = _) => }")
  }

  def testNamedTupleToNormalTuplePattern(): Unit = {
    assertIsIrrefutable("(a = A, b = B) match { case (a, b) => }")
    assertIsIrrefutable("(a = A, b = A -> B) match { case (a, b) => }")
    assertIsIrrefutable("(a = A, b = A -> B) match { case (a, (a2, b)) => }")
    assertIsIrrefutable("(a = A, b = A -> B) match { case (a:A, (a2:A, b: B)) => }")

    assertIsNotIrrefutable("A match { case (a, b) => }")
    assertIsNotIrrefutable("(a = A, b = B, c = B) match { case (a, b) => }")
    assertIsNotIrrefutable("(a = B, b = A) match { case (a: A, b: B) => }", butIsForShallow = true)
    assertIsNotIrrefutable("(a = A, b = B -> A) match { case (_, (a: A, b: B)) => }", butIsForShallow = true)
  }

  def testDirectUnapply(): Unit = {
    val common =
      """
        |class Test extends scala.Product {
        |  def _1: Int = 0
        |  def _2: Int = 2
        |}
        |object Test {
        |  def unapply(x: Int): Test = new Test
        |}
        |
        |""".stripMargin

    assertIsIrrefutable(s"$common 1 match { case Test(x, y) => }")
    assertIsNotIrrefutable(s"$common 1 match { case Test(1, 2) => }", butIsForShallow = true)
    assertIsNotIrrefutable(s"$common 1 match { case Test(x) => }")
  }
}