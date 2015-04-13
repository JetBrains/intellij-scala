package org.jetbrains.plugins.scala.meta

import scala.meta.internal.ast._

import scala.{Seq => _}
import scala.collection.immutable.Seq

class TreeConverterMatchTest extends TreeConverterTestBase {

  def testMatchRef() {
    doTest(
      "a match { case foo => }",
      Term.Match(Term.Name("a"), List(Case(Pat.Var.Term(Term.Name("foo")), None, Term.Block(Nil))))
    )
  }

  def testMatchExtractorSimple() {
    doTest(
      "a match { case Foo(bar, baz) => }",
      Term.Match(Term.Name("a"), List(Case(Pat.Extract(Term.Name("Foo"), Nil,
        List(Pat.Var.Term(Term.Name("bar")), Pat.Var.Term(Term.Name("baz")))), None, Term.Block(Nil))))
    )
  }

  def testMatchExtractorTypedArgs() {
    doTest(
      "a match { case Foo(bar: Int, baz) => }",
      Term.Match(Term.Name("a"), List(Case(Pat.Extract(Term.Name("Foo"), Nil, 
        List(Pat.Typed(Pat.Var.Term(Term.Name("bar")), Type.Name("Int")), Pat.Var.Term(Term.Name("baz")))), None, Term.Block(Nil))))
    )
  }
  
  def testMatchBinding() {
    doTest(
      "a match { case b @ foo() => }",
      Term.Match(Term.Name("a"), List(Case(Pat.Bind(Pat.Var.Term(Term.Name("b")), Pat.Extract(Term.Name("foo"), Nil, Nil)), None, Term.Block(Nil))))
    )
  }
  
  def testMatchTyped() {
    doTest(
      "a match { case b: Int => }",
      Term.Match(Term.Name("a"), List(Case(Pat.Typed(Pat.Var.Term(Term.Name("b")), Type.Name("Int")), None, Term.Block(Nil))))
    )
  }
  
  def testMatchTuple() {
    doTest(
      "a match { case (b, c) => }",
      Term.Match(Term.Name("a"), List(Case(Pat.Tuple(List(Pat.Var.Term(Term.Name("b")), Pat.Var.Term(Term.Name("c")))), None, Term.Block(Nil))))
    )
  }
  
  def testMatchWildCard() {
    doTest(
      "a match { case _ => }",
      Term.Match(Term.Name("a"), List(Case(Pat.Wildcard(), None, Term.Block(Nil))))
    )
  }
  
  def testMatchLiteral() {
    doTest(
      "a match { case 42 => }",
      Term.Match(Term.Name("a"), List(Case(Lit.Int(42), None, Term.Block(Nil))))
    )
  }
  
  def testMatchComposite() {
    doTest(
      "a match { case 1 | 2 | 3 => }",
      Term.Match(Term.Name("a"), List(Case(Pat.Alternative(Lit.Int(1), Pat.Alternative(Lit.Int(2), Lit.Int(3))), None, Term.Block(Nil))))
    )
  }

  def testMatchTypedWildCard() {
    doTest(
      "a match { case _: Int => }",
      Term.Match(Term.Name("a"), List(Case(Pat.Typed(Pat.Wildcard(), Type.Name("Int")), None, Term.Block(Nil))))
    )
  }

  // TODO: type variables
  def testMatchWildCardTypeVar() {
    doTest(
      "a match { case _: Any[t] => }",
      Term.Match(Term.Name("a"), List(Case(Pat.Typed(Pat.Wildcard(), Pat.Type.Apply(Type.Name("Any"), List(Pat.Var.Type(Type.Name("t"))))), None, Term.Block(Nil))))
    )
  }
  
  def testMatchWildCardTypeApplyWildCard() {
    doTest(
      "a match { case _: Any[_] => }",
      Term.Match(Term.Name("a"), List(Case(Pat.Typed(Pat.Wildcard(), Pat.Type.Apply(Type.Name("Any"), List(Pat.Type.Wildcard()))), None, Term.Block(Nil))))
    )
  }

  def testMatchTypeApplyInfix() {
    doTest(
      """
        |def f[T,U] = {
        |//start
        |a match { case _: (T Map U) => }}
      """.stripMargin,
      Term.Block(Seq(Term.Match(Term.Name("a"), List(Case(Pat.Typed(Pat.Wildcard(), Pat.Type.ApplyInfix(Type.Name("T"), Type.Name("Map"), Type.Name("U"))), None, Term.Block(Nil))))))
    )
  }
  
  def testMatchExtractorWildCardSeq() {
    doTest(
      "a match { case foo(_*) => }",
      Term.Match(Term.Name("a"), List(Case(Pat.Extract(Term.Name("foo"), Nil, List(Pat.Arg.SeqWildcard())), None, Term.Block(Nil))))
    )
  }

  def testMatchExtractorBoundWildCardSeq() {
    doTest(
      "a match { case foo(x @ _*) => }",
      Term.Match(Term.Name("a"), List(Case(Pat.Extract(Term.Name("foo"), Nil, List(Pat.Bind(Pat.Var.Term(Term.Name("x")), Pat.Arg.SeqWildcard()))), None, Term.Block(Nil))))
    )
  }
  
  def testMatchInfix() {
    doTest(
      "a match { case a :: b => }",
      Term.Match(Term.Name("a"), List(Case(Pat.ExtractInfix(Pat.Var.Term(Term.Name("a")), Term.Name("::"), List(Pat.Var.Term(Term.Name("b")))), None, Term.Block(Nil))))
    )
  }
}
