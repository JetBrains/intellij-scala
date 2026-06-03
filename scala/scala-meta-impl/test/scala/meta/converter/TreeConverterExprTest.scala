package scala.meta.converter

import scala.meta.{TreeConverterTestBaseWithLibrary, _}
import scala.{Seq => _}

class TreeConverterExprTest extends TreeConverterTestBaseWithLibrary {

  def testIf(): Unit = {
    doTest(
      "if (true) 42",
      Term.If(Lit.Boolean(value = true), Lit.Int(42), Lit.Unit())
    )
  }

  def testIfElse(): Unit = {
    doTest(
      "if (false) 42 else 0",
      Term.If(Lit.Boolean(value = false), Lit.Int(42), Lit.Int(0))
    )
  }

  def testIfElseIfElse(): Unit = {
    doTest(
      "if (true) 42 else if (false) 999 else 0",
      Term.If(Lit.Boolean(value = true), Lit.Int(42), Term.If(Lit.Boolean(value = false), Lit.Int(999), Lit.Int(0)))
    )
  }

  def testObjectMethodCall(): Unit = {
    doTest(
      """object Foo { def f() = 42 }
        |//start
        |Foo.f()
      """.stripMargin,
      Term.Apply(Term.Select(Term.Name("Foo"), Term.Name("f")), Nil)
    )
  }

  def testNewNoParen(): Unit = {
    doTest(
      """class Foo
        |//start
        |new Foo
      """.stripMargin,
      Term.New(Init(Type.Name("Foo"), Name.Anonymous(), Nil))
    )
  }

  def testNewEmptyParen(): Unit = {
    doTest(
      """class Foo
        |//start
        |new Foo()
      """.stripMargin,
      Term.New(Init(Type.Name("Foo"), Name.Anonymous(), List(List.empty)))
    )
  }

  def testNewWithArg(): Unit = {
    doTest(
      """class Foo(a: Int)
        |//start
        |new Foo(42)
      """.stripMargin,
      Term.New(Init(Type.Name("Foo"), Name.Anonymous(), List(List(Lit.Int(42)))))
    )
  }

  def testNewMutiParen(): Unit = {
    doTest(
      """class Foo(a:Int)(b:String)
        |//start
        |new Foo(42)("")""".stripMargin,
      Term.New(Init(Type.Name("Foo"), Name.Anonymous(), List(List(Lit.Int(42)), List(Lit.String("")))))
    )
  }

  def testThrow(): Unit = {
    val exception = Type.Select(Term.Select(Term.Name("java"), Term.Name("lang")), Type.Name("RuntimeException"))
    doTest(
      "throw new java.lang.RuntimeException",
      Term.Throw(Term.New(Init(exception, Name.Anonymous(), List.empty)))
    )
  }

  def testTryCatchFinally(): Unit = {
    doTest(
      """
        |try { () }
        |catch {
        |  case e: Exception => ()
        |  case _ => ()
        |}
        |finally { () }""".stripMargin,
      Term.Try(Term.Block(List(Lit.Unit())), List(Case(Pat.Typed(Pat.Var(Term.Name("e")),
        Type.Name("Exception")), None, Term.Block(List(Lit.Unit()))), Case(Pat.Wildcard(), None,
        Term.Block(List(Lit.Unit())))), Some(Term.Block(List(Lit.Unit()))))
    )
  }

  def testApplyPostfix(): Unit = {
    doTest(
      "Seq() tail",
//      Term.Select(Term.Apply(Term.Name("Seq"), Nil), Term.Name("tail"))  // quasiquote parser emits wrong result?
      Term.Apply(Term.Select(Term.Apply(Term.Name("Seq"), Nil), Term.Name("tail")), Nil)
    )
  }

  def testDoWhile(): Unit = {
    doTest(
      "do {()} while (true)",
      Term.Do(Term.Block(List(Lit.Unit())), Lit.Boolean(true))
    )
  }

  def testWhile(): Unit = {
    doTest(
      "while(true) {()}",
      Term.While(Lit.Boolean(true), Term.Block(List(Lit.Unit())))
    )
  }

  def testForSimple(): Unit = {
    doTest(
      "for(s <- Seq(1)) {42}",
      Term.For(List(Enumerator.Generator(Pat.Var(Term.Name("s")),
        Term.Apply(Term.Name("Seq"), List(Lit.Int(1))))), Term.Block(List(Lit.Int(42))))
    )
  }

  def testForMutiWithGuards(): Unit = {
    doTest(
      "for (s: Int <- Seq(1); y <- Seq(3) if y == s; z = (s, y)) {}",
      Term.For(List(
        Enumerator.Generator(Pat.Typed(Pat.Var(Term.Name("s")), Type.Name("Int")), Term.Apply(Term.Name("Seq"), List(Lit.Int(1)))),
        Enumerator.Generator(Pat.Var(Term.Name("y")), Term.Apply(Term.Name("Seq"), List(Lit.Int(3)))),
        Enumerator.Guard(Term.ApplyInfix(Term.Name("y"), Term.Name("=="), Nil, List(Term.Name("s")))),
        Enumerator.Val(Pat.Var(Term.Name("z")), Term.Tuple(List(Term.Name("s"), Term.Name("y"))))),
        Term.Block(Nil))
    )
  }

  def testSuperReference(): Unit = {
    val emptyTemplate = Template(Nil, Nil, Self(Name.Anonymous(), None), Nil)
    val ctor = Ctor.Primary(Nil, Name.Anonymous(), Nil)
    val A = Term.Name("A")
    val Foo = Type.Name("Foo")
    val Bar = Type.Name("Bar")
    val Baz = Type.Name("Baz")

    val defn1 = Defn.Trait(Nil, Foo, Nil, ctor, emptyTemplate)
    val defn2 = Defn.Trait(Nil, Bar, Nil, ctor, emptyTemplate)

    val AFoo = Init(Type.Select(A, Foo), Name.Anonymous(), Nil)
    val ABar = Init(Type.Select(A, Bar), Name.Anonymous(), Nil)
    val defn3Stat = Term.Select(Term.Super(Name.Indeterminate("Baz"), Name.Indeterminate("Foo")), Term.Name("hashCode"))
    val defn3Template = Template(Nil, List(AFoo, ABar), Self(Name.Anonymous(), None), List(defn3Stat))
    val defn3 = Defn.Class(Nil, Baz, Nil, ctor, defn3Template)

    val defns = List(defn1, defn2, defn3)
    doTest(
      """
        |object A {
        |trait Foo
        |trait Bar
        |class Baz extends A.Foo with A.Bar { Baz.super[Foo].hashCode }}
        |""".stripMargin,
      Defn.Object(Nil, Term.Name("A"), emptyTemplate.copy(stats = defns))
    )
  }

  def testThisReference(): Unit = {
    doTest(
      "trait Foo { def foo: Int = this.hashCode }",
      Defn.Trait(Nil, Type.Name("Foo"), Nil, Ctor.Primary(Nil, Term.Name("this"), Nil),
        Template(Nil, Nil, Self(Name.Anonymous(), None),
          List(Defn.Def(Nil, Term.Name("foo"), Nil, Nil, Some(Type.Name("Int")),
            Term.Select(Term.This(Name.Anonymous()), Term.Name("hashCode"))))))
    )
  }


  def testPatrialFunction(): Unit = {
    doTest(
      "foo {case x => x}",
      Term.Apply(Term.Name("foo"), List(Term.PartialFunction(List(Case(Pat.Var(Term.Name("x")), None, Term.Name("x"))))))
    )
  }

  def testAssignment(): Unit = {
    doTest(
      "someVar = value",
      Term.Assign(Term.Name("someVar"), Term.Name("value"))
    )
  }

  def testTypedExpr(): Unit = {
    doTest(
      "foo(arg: Tpe)",
      Term.Apply(Term.Name("foo"), List(Term.Ascribe(Term.Name("arg"), Type.Name("Tpe"))))
    )
  }

  def testRepeatedTyped(): Unit = {
    doTest(
      "foo(args: _*)",
      Term.Apply(Term.Name("foo"), List(Term.Repeated(Term.Name("args"))))
    )
  }
}
