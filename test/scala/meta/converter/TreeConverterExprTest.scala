package scala.meta.converter

import scala.meta.{TreeConverterTestBaseWithLibrary, TreeConverterTestBaseNoLibrary}
import scala.meta._
import scala.collection.immutable.Seq
import scala.{Seq => _}

class TreeConverterExprTest extends TreeConverterTestBaseWithLibrary {

  def testIf() {
    doTest(
      "if (true) 42",
      Term.If(Lit(value = true), Lit(42), Lit(()))
    )
  }
  
  def testIfElse() {
    doTest(
      "if (false) 42 else 0",
      Term.If(Lit(value = false), Lit(42), Lit(0))
    )
  }
  
  def testIfElseIfElse() {
    doTest(
      "if (true) 42 else if (false) 999 else 0",
      Term.If(Lit(value = true), Lit(42), Term.If(Lit(value = false), Lit(999), Lit(0)))
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
      Term.New(Template(Nil, List(Term.Apply(Ctor.Ref.Name("Foo"), List(Lit(42)))), Term.Param(Nil, Name.Anonymous(), None, None), None))
    )
  }

  def testNewMutiParen() {
    doTest(
      """class Foo(a:Int)(b:String)
        |//start
        |new Foo(42)("")""".stripMargin,
      Term.New(Template(Nil, List(Term.Apply(Term.Apply(Ctor.Ref.Name("Foo"), List(Lit(42))), List(Lit("")))), Term.Param(Nil, Name.Anonymous(), None, None), None))
    )
  }

  def testThrow(): Unit = {
    doTest(
      "throw new java.lang.RuntimeException",
      Term.Throw(Term.New(Template(Nil, List(Ctor.Ref.Select(Term.Select(Term.Name("java"),
        Term.Name("lang")), Ctor.Ref.Name("RuntimeException"))),
        Term.Param(Nil, Name.Anonymous(), None, None), None)))
    )
  }

  def testTryCatchFinally() {
    doTest(
      """
        |try { () }
        |catch {
        |  case e: Exception => ()
        |  case _ => ()
        |}
        |finally { () }""".stripMargin,
      Term.TryWithCases(Term.Block(List(Lit(()))), List(Case(Pat.Typed(Pat.Var.Term(Term.Name("e")),
        Type.Name("Exception")), None, Term.Block(List(Lit(())))), Case(Pat.Wildcard(), None,
        Term.Block(List(Lit(()))))), Some(Term.Block(List(Lit(())))))
    )
  }
  
  def testApplyPostfix() {
    doTest(
      "Seq() tail",
//      Term.Select(Term.Apply(Term.Name("Seq"), Nil), Term.Name("tail"))  // quasiquote parser emits wrong result?
      Term.Apply(Term.Select(Term.Apply(Term.Name("Seq"), Nil), Term.Name("tail")), Nil)
    )
  }
  
  def testDoWhile() {
    doTest(
      "do {()} while (true)",
      Term.Do(Term.Block(List(Lit(()))), Lit(true))
    )
  }
  
  def testWhile() {
    doTest(
      "while(true) {()}",
      Term.While(Lit(true), Term.Block(List(Lit(()))))
    )
  }

  def testForSimple() {
    doTest(
      "for(s <- Seq(1)) {42}",
      Term.For(List(Enumerator.Generator(Pat.Var.Term(Term.Name("s")),
        Term.Apply(Term.Name("Seq"), List(Lit(1))))), Term.Block(List(Lit(42))))
    )
  }
  
  def testForMutiWithGuards() {
    doTest(
      "for (s: Int <- Seq(1); y <- Seq(3) if y == s; z = (s, y)) {}",
      Term.For(List(
        Enumerator.Generator(Pat.Typed(Pat.Var.Term(Term.Name("s")), Type.Name("Int")), Term.Apply(Term.Name("Seq"), List(Lit(1)))),
        Enumerator.Generator(Pat.Var.Term(Term.Name("y")), Term.Apply(Term.Name("Seq"), List(Lit(3)))),
        Enumerator.Guard(Term.ApplyInfix(Term.Name("y"), Term.Name("=="), Nil, List(Term.Name("s")))),
        Enumerator.Val(Pat.Var.Term(Term.Name("z")), Term.Tuple(List(Term.Name("s"), Term.Name("y"))))),
        Term.Block(Nil))
    )
  }

  def testSuperReference() {
    doTest(
      """
        |object A {
        |trait Foo
        |trait Bar
        |class Baz extends A.Foo with A.Bar { Baz.super[Foo].hashCode }}
        |""".stripMargin,
      Defn.Object(Nil, Term.Name("A"),
        Template(Nil, Nil, Term.Param(Nil, Name.Anonymous(), None, None), Some(
          List(Defn.Trait(Nil, Type.Name("Foo"), Nil, Ctor.Primary(Nil, Ctor.Ref.Name("this"), Nil),
            Template(Nil, Nil, Term.Param(Nil, Name.Anonymous(), None, None), None)),
            Defn.Trait(Nil, Type.Name("Bar"), Nil, Ctor.Primary(Nil, Ctor.Ref.Name("this"), Nil),
              Template(Nil, Nil, Term.Param(Nil, Name.Anonymous(), None, None), None)),
            Defn.Class(Nil, Type.Name("Baz"), Nil, Ctor.Primary(Nil, Ctor.Ref.Name("this"), Nil),
              Template(Nil, List(Ctor.Ref.Name("Foo"), Ctor.Ref.Name("Bar")),
                Term.Param(Nil, Name.Anonymous(), None, None),
                Some(List(Term.Select(Term.Super(Name.Indeterminate("Baz"),
                  Name.Indeterminate("Foo")), Term.Name("hashCode")))))))
        ))))
  }

  def testThisReference() {
    doTest(
      "trait Foo { def foo: Int = this.hashCode }",
      Defn.Trait(Nil, Type.Name("Foo"), Nil, Ctor.Primary(Nil, Ctor.Ref.Name("this"), Nil),
        Template(Nil, Nil, Term.Param(Nil, Name.Anonymous(), None, None),
          Some(Seq(Defn.Def(Nil, Term.Name("foo"), Nil, Nil, Some(Type.Name("Int")),
            Term.Select(Term.This(Name.Anonymous()), Term.Name("hashCode")))))))
    )
  }


}
