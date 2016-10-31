package scala.meta.converter

import scala.meta.TreeConverterTestBaseWithLibrary
import scala.meta._
import scala.{Seq => _}

class TreeConverterMatchTest extends TreeConverterTestBaseWithLibrary {

  def testMatchRef() {
    doTest(
      """
        |val a = 42
        |//start
        |a match { case foo => }
      """.stripMargin,
      Term.Match(Term.Name("a"), List(Case(Pat.Var.Term(Term.Name("foo")), None, Term.Block(Nil))))
    )
  }

  def testMatchExtractorSimple() {
    doTest(
      """def f = {
        |case class Foo(a: Any, b: Any)
        |val a = Foo(1,2)
        |//start
        |a match { case Foo(bar, baz) => }}
      """.stripMargin,
      Term.Match(Term.Name("a"), List(Case(Pat.Extract(Term.Name("Foo"), Nil,
        List(Pat.Var.Term(Term.Name("bar")), Pat.Var.Term(Term.Name("baz")))), None, Term.Block(Nil))))
    )
  }

  def testMatchExtractorTypedArgs() {
    doTest(
      """
        |val a = 42
        |//start
        |a match { case Some(bar: Int) => }
      """.stripMargin,
      Term.Match(Term.Name("a"), List(Case(Pat.Extract(Term.Name("Some"), Nil,
        List(Pat.Typed(Pat.Var.Term(Term.Name("bar")), Type.Name("Int")))), None, Term.Block(Nil))))
    )
  }
  
  def testMatchBinding() {
    doTest(
      """
        |val a = 42
        |//start
        |a match { case b @ Some() => }
      """.stripMargin,
      Term.Match(Term.Name("a"), List(Case(Pat.Bind(Pat.Var.Term(Term.Name("b")), Pat.Extract(Term.Name("Some"), Nil, Nil)), None, Term.Block(Nil))))
    )
  }
  
  def testMatchTyped() {
    doTest(
      "42 match { case b: Int => }",
      Term.Match(Lit(42), List(Case(Pat.Typed(Pat.Var.Term(Term.Name("b")), Type.Name("Int")), None, Term.Block(Nil))))
    )
  }
  
  def testMatchTuple() {
    doTest(
      "(42, 24) match { case (b, c) => }",
      Term.Match(Term.Tuple(List(Lit(42), Lit(24))) , List(Case(Pat.Tuple(List(Pat.Var.Term(Term.Name("b")), Pat.Var.Term(Term.Name("c")))), None, Term.Block(Nil))))
    )
  }
  
  def testMatchWildCard() {
    doTest(
      "42 match { case _ => }",
      Term.Match(Lit(42), List(Case(Pat.Wildcard(), None, Term.Block(Nil))))
    )
  }
  
  def testMatchLiteral() {
    doTest(
      "42 match { case 42 => }",
      Term.Match(Lit(42), List(Case(Lit(42), None, Term.Block(Nil))))
    )
  }
  
  def testMatchComposite() {
    doTest(
      "42 match { case 1 | 2 | 3 => }",
      Term.Match(Lit(42), List(Case(Pat.Alternative(Lit(1), Pat.Alternative(Lit(2), Lit(3))), None, Term.Block(Nil))))
    )
  }

  def testMatchTypedWildCard() {
    doTest(
      "42 match { case _: Int => }",
      Term.Match(Lit(42), List(Case(Pat.Typed(Pat.Wildcard(), Type.Name("Int")), None, Term.Block(Nil))))
    )
  }

  // TODO: type variables
  // FIXME: disabled until semantics engine is implemented
  def testMatchWildCardTypeVar() {
    return
    doTest(
      "42 match { case _: Any[t] => }",
      Term.Match(Lit(42), List(Case(Pat.Typed(Pat.Wildcard(), Pat.Type.Apply(Type.Name("Any"), List(Pat.Var.Type(Type.Name("t"))))), None, Term.Block(Nil))))
    )
  }
  
  def testMatchWildCardTypeApplyWildCard() {
    doTest(
      "42 match { case _: Any[_] => }",
      Term.Match(Lit(42), List(Case(Pat.Typed(Pat.Wildcard(), Pat.Type.Apply(Type.Name("Any"), List(Pat.Type.Wildcard()))), None, Term.Block(Nil))))
    )
  }

  def testMatchTypeApplyInfix() {
    doTest(
      """
        |def f[T,U] = {
        |//start
        |42 match { case _: (T Map U) => }}
      """.stripMargin,
      Term.Match(Lit(42), List(Case(Pat.Typed(Pat.Wildcard(), Pat.Type.ApplyInfix(Type.Name("T"), Type.Name("Map"), Type.Name("U"))), None, Term.Block(Nil))))
    )
  }
  
  def testMatchExtractorWildCardSeq() {
    doTest(
      "42 match { case Some(_*) => }",
      Term.Match(Lit(42), List(Case(Pat.Extract(Term.Name("Some"), Nil, List(Pat.Arg.SeqWildcard())), None, Term.Block(Nil))))
    )
  }

  def testMatchExtractorBoundWildCardSeq() {
    doTest(
      "42 match { case Some(x @ _*) => }",
      Term.Match(Lit(42), List(Case(Pat.Extract(Term.Name("Some"), Nil, List(Pat.Bind(Pat.Var.Term(Term.Name("x")), Pat.Arg.SeqWildcard()))), None, Term.Block(Nil))))
    )
  }
  
  def testMatchInfix() {
    doTest(
      "42 match { case a :: b => }",
      Term.Match(Lit(42), List(Case(Pat.ExtractInfix(Pat.Var.Term(Term.Name("a")), Term.Name("::"), List(Pat.Var.Term(Term.Name("b")))), None, Term.Block(Nil))))
    )
  }
}
