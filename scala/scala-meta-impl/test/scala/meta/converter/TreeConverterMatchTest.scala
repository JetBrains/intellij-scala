package scala.meta.converter

import scala.meta.{TreeConverterTestBaseWithLibrary, _}
import scala.{Seq => _}

class TreeConverterMatchTest extends TreeConverterTestBaseWithLibrary {

  def testMatchRef(): Unit = {
    doTest(
      """
        |val a = 42
        |//start
        |a match { case foo => }
      """.stripMargin,
      Term.Match(Term.Name("a"), List(Case(Pat.Var(Term.Name("foo")), None, Term.Block(Nil))))
    )
  }

  def testMatchExtractorSimple(): Unit = {
    doTest(
      """def f = {
        |case class Foo(a: Any, b: Any)
        |val a = Foo(1,2)
        |//start
        |a match { case Foo(bar, baz) => }}
      """.stripMargin,
      Term.Match(Term.Name("a"), List(Case(Pat.Extract(Term.Name("Foo"),
        List(Pat.Var(Term.Name("bar")), Pat.Var(Term.Name("baz")))), None, Term.Block(Nil))))
    )
  }

  def testMatchExtractorTypedArgs(): Unit = {
    doTest(
      """
        |val a = 42
        |//start
        |a match { case Some(bar: Int) => }
      """.stripMargin,
      Term.Match(Term.Name("a"), List(Case(Pat.Extract(Term.Name("Some"),
        List(Pat.Typed(Pat.Var(Term.Name("bar")), Type.Name("Int")))), None, Term.Block(Nil))))
    )
  }

  def testMatchBinding(): Unit = {
    doTest(
      """
        |val a = 42
        |//start
        |a match { case b @ Some() => }
      """.stripMargin,
      Term.Match(Term.Name("a"), List(Case(Pat.Bind(Pat.Var(Term.Name("b")), Pat.Extract(Term.Name("Some"), Nil)), None, Term.Block(Nil))))
    )
  }

  def testMatchTyped(): Unit = {
    doTest(
      "42 match { case b: Int => }",
      Term.Match(Lit.Int(42), List(Case(Pat.Typed(Pat.Var(Term.Name("b")), Type.Name("Int")), None, Term.Block(Nil))))
    )
  }

  def testMatchTuple(): Unit = {
    doTest(
      "(42, 24) match { case (b, c) => }",
      Term.Match(Term.Tuple(List(Lit.Int(42), Lit.Int(24))) , List(Case(Pat.Tuple(List(Pat.Var(Term.Name("b")), Pat.Var(Term.Name("c")))), None, Term.Block(Nil))))
    )
  }

  def testMatchWildCard(): Unit = {
    doTest(
      "42 match { case _ => }",
      Term.Match(Lit.Int(42), List(Case(Pat.Wildcard(), None, Term.Block(Nil))))
    )
  }

  def testMatchLiteral(): Unit = {
    doTest(
      "42 match { case 42 => }",
      Term.Match(Lit.Int(42), List(Case(Lit.Int(42), None, Term.Block(Nil))))
    )
  }

  def testMatchComposite(): Unit = {
    doTest(
      "42 match { case 1 | 2 | 3 => }",
      Term.Match(Lit.Int(42), List(Case(Pat.Alternative(Lit.Int(1), Pat.Alternative(Lit.Int(2), Lit.Int(3))), None, Term.Block(Nil))))
    )
  }

  def testMatchTypedWildCard(): Unit = {
    doTest(
      "42 match { case _: Int => }",
      Term.Match(Lit.Int(42), List(Case(Pat.Typed(Pat.Wildcard(), Type.Name("Int")), None, Term.Block(Nil))))
    )
  }

  // TODO: type variables
  // FIXME: disabled until semantics engine is implemented
  def testMatchWildCardTypeVar(): Unit = {
    return
//    doTest(
//      "42 match { case _: Any[t] => }",
//      Term.Match(Lit.Int(42), List(Case(Pat.Typed(Pat.Wildcard(), Pat.Type.Apply(Type.Name("Any"), List(Pat.Var.Type(Type.Name("t"))))), None, Term.Block(Nil))))
//    )
  }

//  def testMatchWildCardTypeApplyWildCard(): Unit = {
//    doTest(
//      "42 match { case _: Any[_] => }",
//      Term.Match(Lit.Int(42), List(Case(Pat.Typed(Pat.Wildcard(), Type.Apply(Type.Name("Any"), List(Type.Placeholder(Type.Bounds(None, None))))), None, Term.Block(Nil))))
//    )
//  }

  def testMatchTypeApplyInfix(): Unit = {
    doTest(
      """
        |def f[T,U] = {
        |//start
        |42 match { case _: (T Map U) => }}
      """.stripMargin,
      Term.Match(Lit.Int(42), List(Case(Pat.Typed(Pat.Wildcard(), Type.ApplyInfix(Type.Name("T"), Type.Name("Map"), Type.Name("U"))), None, Term.Block(Nil))))
    )
  }

  def testMatchExtractorWildCardSeq(): Unit = {
    doTest(
      "42 match { case Some(_*) => }",
      Term.Match(Lit.Int(42), List(Case(Pat.Extract(Term.Name("Some"), List(Pat.SeqWildcard())), None, Term.Block(Nil))))
    )
  }

  def testMatchExtractorBoundWildCardSeq(): Unit = {
    doTest(
      "42 match { case Some(x @ _*) => }",
      Term.Match(Lit.Int(42), List(Case(Pat.Extract(Term.Name("Some"), List(Pat.Bind(Pat.Var(Term.Name("x")), Pat.SeqWildcard()))), None, Term.Block(Nil))))
    )
  }

  def testMatchInfix(): Unit = {
    doTest(
      "42 match { case a :: b => }",
      Term.Match(Lit.Int(42), List(Case(Pat.ExtractInfix(Pat.Var(Term.Name("a")), Term.Name("::"), List(Pat.Var(Term.Name("b")))), None, Term.Block(Nil))))
    )
  }
}
