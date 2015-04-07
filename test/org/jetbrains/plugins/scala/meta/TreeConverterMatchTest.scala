package org.jetbrains.plugins.scala.meta

import scala.meta.internal.ast._

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

}
