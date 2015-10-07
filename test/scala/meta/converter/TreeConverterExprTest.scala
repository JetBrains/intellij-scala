package scala.meta.converter

import scala.meta.TreeConverterTestBaseNoLibrary
import scala.meta.internal.ast._

class TreeConverterExprTest extends TreeConverterTestBaseNoLibrary {

  def testIf() {
    doTest(
      "if (true) 42",
      Term.If(Lit.Bool(value = true), Lit.Int(42), Lit.Unit())
    )
  }
  
  def testIfElse() {
    doTest(
      "if (false) 42 else 0",
      Term.If(Lit.Bool(value = false), Lit.Int(42), Lit.Int(0))
    )
  }
  
  def testIfElseIfElse() {
    doTest(
      "if (true) 42 else if (false) 999 else 0",
      Term.If(Lit.Bool(value = true), Lit.Int(42), Term.If(Lit.Bool(value = false), Lit.Int(999), Lit.Int(0)))
    )
  }

  def testObjectMethodCall() {
    doTest(
      """object Foo { def f() = 42 }
        |//start
        |Foo.f()
      """.stripMargin,
      Term.Apply(Term.Select(Term.Name("Foo"), Term.Name("f")), Nil)
    )
  }
  
  def testNewNoParen() {
    doTest(
      """class Foo
        |//start
        |new Foo
      """.stripMargin,
      Term.New(Template(Nil, List(Ctor.Ref.Name("Foo")), Term.Param(Nil, Name.Anonymous(), None, None), None))
    )
  }

  def testNewEmptyParen() {
    doTest(
      """class Foo
        |//start
        |new Foo()
      """.stripMargin,
      Term.New(Template(Nil, List(Term.Apply(Ctor.Ref.Name("Foo"), Nil)), Term.Param(Nil, Name.Anonymous(), None, None), None))
    )
  }
  
  def testNewWithArg() {
    doTest(
      """class Foo(a: Int)
        |//start
        |new Foo(42)
      """.stripMargin,
      Term.New(Template(Nil, List(Term.Apply(Ctor.Ref.Name("Foo"), List(Lit.Int(42)))), Term.Param(Nil, Name.Anonymous(), None, None), None))
    )
  }

  def testNewMutiParen() {
    doTest(
      """class Foo(a:Int)(b:String)
        |//start
        |new Foo(42)("")""".stripMargin,
      Term.New(Template(Nil, List(Term.Apply(Term.Apply(Ctor.Ref.Name("Foo"), List(Lit.Int(42))), List(Lit.String("")))), Term.Param(Nil, Name.Anonymous(), None, None), None))
    )
  }

  def testThrow(): Unit = {
    doTest(
      """
        |import java.lang.Exception
        |//start
        |throw new scala.Exception()""".stripMargin,
      Lit.Unit()
    )
  }

  def testTryCatchFinally() {
    doTest(
      """
        |try {
        |  ()
        |catch {
        |  case e: Exception => ()
        |  case _ => ()
        |}
        |finally {
        |  ()
        |}""".stripMargin,
      Lit.Unit()
    )
  }
  
  def testApplyPostfix() {
    doTest(
      "Seq() tail",
      Term.Select(Term.Apply(Term.Name("Seq"), Nil), Term.Name("tail"))
    )
  }
  
  def testDoWhile() {
    doTest(
      "do {()} while (true)",
      Term.Do(Term.Block(List(Lit.Unit())), Lit.Bool(true))
    )
  }
  
  def testWhile() {
    doTest(
      "while(true) {()}",
      Term.While(Lit.Bool(true), Term.Block(List(Lit.Unit())))
    )
  }

  def testForSimple() {
    for (s <- Seq() if s != null; y <- Seq() if y == s) {}
    doTest(
      "for(s <- Seq()) {42}",
      Term.For(List(Enumerator.Generator(Pat.Var.Term(Term.Name("s")), Term.Apply(Term.Name("Seq"), Nil))),
        Term.Block(List(Lit.Int(42))))
    )
  }
  
  def testForMutiWithGuards() {
    doTest(
      "for (s <- Seq() if s != null; y <- Seq() if y == s) {}",
      Term.For(List(Enumerator.Generator(Pat.Var.Term(Term.Name("s")), Term.Apply(Term.Name("Seq"), Nil)),
        Enumerator.Guard(Term.ApplyInfix(Term.Name("s"), Term.Name("!="), Nil, List(Lit.Null()))),
        Enumerator.Generator(Pat.Var.Term(Term.Name("y")), Term.Apply(Term.Name("Seq"), Nil)),
        Enumerator.Guard(Term.ApplyInfix(Term.Name("y"), Term.Name("=="), Nil, List(Term.Name("s"))))),
        Term.Block(Nil))
    )
  }

  def testSuperReference() {
    doTest(
      """
        |trait Foo
        |trait Bar
        |class Baz extends Foo with Bar { Baz.super[Foo].hashCode }
        |""".stripMargin,
    Term.Block(
      List(Defn.Trait(Nil, Type.Name("Foo"), Nil, Ctor.Primary(Nil, Ctor.Ref.Name("this"), Nil),
      Template(Nil, Nil, Term.Param(Nil, Name.Anonymous(), None, None), None)),
      Defn.Trait(Nil, Type.Name("Bar"), Nil, Ctor.Primary(Nil, Ctor.Ref.Name("this"), Nil),
        Template(Nil, Nil, Term.Param(Nil, Name.Anonymous(), None, None), None)),
        Defn.Class(Nil, Type.Name("Baz"), Nil, Ctor.Primary(Nil, Ctor.Ref.Name("this"), Nil),
          Template(Nil, List(Ctor.Ref.Name("Foo"), Ctor.Ref.Name("Bar")),
            Term.Param(Nil, Name.Anonymous(), None, None),
            Some(List(Term.Select(Term.Super(Name.Indeterminate("Baz"),
              Name.Indeterminate("Foo")), Term.Name("hashCode"))))))))
    )
  }

  def testThisReference() {
    doTest(
      "trait Foo { this.hashCode }",
      Defn.Trait(Nil, Type.Name("Foo"), Nil, Ctor.Primary(Nil, Ctor.Ref.Name("this"), Nil),
        Template(Nil, Nil, Term.Param(Nil, Name.Anonymous(), None, None),
          Some(List(Defn.Def(Nil, Term.Name("foo"), Nil, Nil, Some(Type.Name("Int")),
            Term.Select(Term.This(Name.Indeterminate("Foo")), Term.Name("foo")))))))
    )
  }


}
