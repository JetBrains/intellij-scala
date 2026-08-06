package scala.meta.converter

import scala.meta.{TreeConverterTestBaseNoLibrary, _}

class TreeConverterTemplateTest extends TreeConverterTestBaseNoLibrary {

  def testTraitEmpty(): Unit = {
    doTest(
      "trait A",
      Defn.Trait(Nil, Type.Name("A"), Nil, Ctor.Primary(Nil, Name.Anonymous(), Nil),
        Template(Nil, Nil, Self(Name.Anonymous(), None), Nil))
    )
  }

  def testTraitEmptyNone(): Unit = {
    doTest(
      "trait T {}",
      Defn.Trait(Nil, Type.Name("T"), Nil, Ctor.Primary(Nil, Name.Anonymous(), Nil),
        Template(Nil, Nil, Self(Name.Anonymous(), None), Nil))
    )
  }

  def testTraitTyped(): Unit = {
    doTest(
      "trait T[A]",
      Defn.Trait(Nil, Type.Name("T"), List(Type.Param(Nil, Type.Name("A"), Nil, Type.Bounds(None, None), Nil, Nil)),
        Ctor.Primary(Nil, Name.Anonymous(), Nil), Template(Nil, Nil, Self(Name.Anonymous(), None), Nil))
    )
  }

  def testTraitExtends(): Unit = {
    doTest(
    """
      |trait B
      |//start
      |trait A extends B
    """.stripMargin,
      Defn.Trait(Nil, Type.Name("A"), Nil, Ctor.Primary(Nil, Name.Anonymous(), Nil),
        Template(Nil, List(Init(Type.Name("B"), Term.Name("B"), Nil)), Self(Name.Anonymous(), None), Nil))
    )
  }

  def testTraitExtendsEarly(): Unit = {
    doTest(
      """
        |trait B
        |//start
        |trait A extends { val x: Int = 42 } with B
      """.stripMargin,
       Defn.Trait(Nil, Type.Name("A"), Nil, Ctor.Primary(Nil, Name.Anonymous(), Nil),
         Template(List(Defn.Val(Nil, List(Pat.Var(Term.Name("x"))), Some(Type.Name("Int")), Lit.Int(42))),
           List(Init(Type.Name("B"), Term.Name("B"), Nil)), Self(Name.Anonymous(), None), Nil))
    )
  }

  def testTraitSelf(): Unit = {
    doTest(
      """
        |trait B
        |//start
        |trait A { self: B => }
      """.stripMargin,
      Defn.Trait(Nil, Type.Name("A"), Nil, Ctor.Primary(Nil, Name.Anonymous(), Nil),
        Template(Nil, Nil, Self(Term.Name("self"), Some(Type.Name("B"))), Nil))
    )
  }

  def testTraitTemplateBody(): Unit = {
    doTest(
      "trait T { def x: Int }",
      Defn.Trait(Nil, Type.Name("T"), Nil, Ctor.Primary(Nil, Name.Anonymous(), Nil),
        Template(Nil, Nil, Self(Name.Anonymous(), None),
          List(Decl.Def(Nil, Term.Name("x"), Nil, Nil, Type.Name("Int")))))
    )
  }

  def testTraitTemplateExprs(): Unit = {
    doTest(
      "trait T { def f = 1 ; f()}",
      Defn.Trait(Nil, Type.Name("T"), Nil, Ctor.Primary(Nil, Name.Anonymous(), Nil),
        Template(Nil, Nil, Self(Name.Anonymous(), None),
          List(Defn.Def(Nil, Term.Name("f"), Nil, Nil, None, Lit.Int(1)), Term.Apply(Term.Name("f"), Nil))))
    )
  }

  def testClassEmpty(): Unit = {
    doTest(
      "class C",
      Defn.Class(Nil, Type.Name("C"), Nil, Ctor.Primary(Nil, Name.Anonymous(), Nil),
        Template(Nil, Nil, Self(Name.Anonymous(), None), Nil))
    )
  }

  def testClassParametrized(): Unit = {
    doTest(
      "class C[T]",
      Defn.Class(Nil, Type.Name("C"), List(Type.Param(Nil, Type.Name("T"), Nil, Type.Bounds(None, None), Nil, Nil)),
        Ctor.Primary(Nil, Name.Anonymous(), Nil), Template(Nil, Nil, Self(Name.Anonymous(), None), Nil))
    )
  }

  def testClassExtends(): Unit = {
    doTest(
      """
        |class B
        |//start
        |class A extends B
      """.stripMargin,
      Defn.Class(Nil, Type.Name("A"), Nil, Ctor.Primary(Nil, Name.Anonymous(), Nil),
        Template(Nil, List(Init(Type.Name("B"), Term.Name("B"), Nil)), Self(Name.Anonymous(), None), Nil))
    )
  }

  def testNestedClass(): Unit = {
    doTest(
      "class A { class B { class C }}}",
      Defn.Class(Nil, Type.Name("A"), Nil, Ctor.Primary(Nil, Name.Anonymous(), Nil),
        Template(Nil, Nil, Self(Name.Anonymous(), None),
          List(Defn.Class(Nil, Type.Name("B"), Nil, Ctor.Primary(Nil, Name.Anonymous(), Nil),
          Template(Nil, Nil, Self(Name.Anonymous(), None),
            List(Defn.Class(Nil, Type.Name("C"), Nil, Ctor.Primary(Nil, Name.Anonymous(), Nil),
            Template(Nil, Nil, Self(Name.Anonymous(), None), Nil))))))))
    )
  }

  def testClassMixedChildren(): Unit = {
    doTest(
//       FIXME: member/expr order preser vation not working for now, exprs will always go after members
      "class A {val a = 42; class B; def f = 42; type T; trait Foo; 42; f(a)}",
      Defn.Class(Nil, Type.Name("A"), Nil, Ctor.Primary(Nil, Name.Anonymous(), Nil),
        Template(Nil, Nil, Self(Name.Anonymous(), None),
          List(Defn.Val(Nil, List(Pat.Var(Term.Name("a"))), None, Lit.Int(42)),
            Defn.Class(Nil, Type.Name("B"), Nil, Ctor.Primary(Nil, Name.Anonymous(), Nil),
              Template(Nil, Nil, Self(Name.Anonymous(), None), Nil)),
            Defn.Def(Nil, Term.Name("f"), Nil, Nil, None, Lit.Int(42)),
            Decl.Type(Nil, Type.Name("T"), Nil, Type.Bounds(None, None)),
            Defn.Trait(Nil, Type.Name("Foo"), Nil, Ctor.Primary(Nil, Name.Anonymous(), Nil),
              Template(Nil, Nil, Self(Name.Anonymous(), None), Nil)), Lit.Int(42),
            Term.Apply(Term.Name("f"), List(Term.Name("a"))))))
    )
  }


  def testClassEarlyDefn(): Unit = {
    doTest(
      """
        |class B
        |//start
        |class A extends { val x: Int = 42 } with B
      """.stripMargin,
      Defn.Class(Nil, Type.Name("A"), Nil, Ctor.Primary(Nil, Name.Anonymous(), Nil),
        Template(List(Defn.Val(Nil, List(Pat.Var(Term.Name("x"))),
          Some(Type.Name("Int")), Lit.Int(42))), List(Init(Type.Name("B"), Term.Name("B"), Nil)), Self(Name.Anonymous(), None), Nil))
    )
  }

  def testClassSelf(): Unit = {
    doTest(
      """
        |class B
        |//start
        |class A { self: B => }
      """.stripMargin,
       Defn.Class(Nil, Type.Name("A"), Nil, Ctor.Primary(Nil, Name.Anonymous(), Nil),
         Template(Nil, Nil, Self(Term.Name("self"), Some(Type.Name("B"))), Nil))
    )
  }

//  def testClassThisSelf() {
//    doTest(
//      "class A { this => }",
//      Defn.Class(Nil, Type.Name("A"), Nil, Ctor.Primary(Nil, Ctor.Ref.Name("this"), Nil),
//        Template(Nil, Nil, Term.Param(Nil, Name.Anonymous(), None, None), Some(Nil)))
//    )
//  }

  def testClassTemplate(): Unit = {
    doTest(
      "class C { def x: Int }",
       Defn.Class(Nil, Type.Name("C"), Nil, Ctor.Primary(Nil, Name.Anonymous(), Nil),
         Template(Nil, Nil, Self(Name.Anonymous(), None),
           List(Decl.Def(Nil, Term.Name("x"), Nil, Nil, Type.Name("Int")))))
    )
  }

  def testClassCtor(): Unit = {
    doTest(
      "class C(x: Int)",
       Defn.Class(Nil, Type.Name("C"), Nil, Ctor.Primary(Nil, Name.Anonymous(),
         List(List(Term.Param(Nil, Term.Name("x"), Some(Type.Name("Int")), None)))),
         Template(Nil, Nil, Self(Name.Anonymous(), None), Nil))
    )
  }

  def testClassCtorPrivate(): Unit = {
    doTest(
      "class C private(x: Int)",
      Defn.Class(Nil, Type.Name("C"), Nil, Ctor.Primary(List(Mod.Private(Name.Anonymous())),
        Name.Anonymous(), List(List(Term.Param(Nil, Term.Name("x"), Some(Type.Name("Int")), None)))),
        Template(Nil, Nil, Self(Name.Anonymous(), None), Nil))
    )
  }

  def testClassValParam(): Unit = {
    doTest(
      "class C(val x: Int)",
      Defn.Class(Nil, Type.Name("C"), Nil, Ctor.Primary(Nil, Name.Anonymous(),
        List(List(Term.Param(List(Mod.ValParam()), Term.Name("x"), Some(Type.Name("Int")), None)))),
        Template(Nil, Nil, Self(Name.Anonymous(), None), Nil))
    )
  }

  def testClassVarParam(): Unit = {
    doTest(
      "class C(var x: Int)",
      Defn.Class(Nil, Type.Name("C"), Nil, Ctor.Primary(Nil, Name.Anonymous(),
        List(List(Term.Param(List(Mod.VarParam()), Term.Name("x"), Some(Type.Name("Int")), None)))),
        Template(Nil, Nil, Self(Name.Anonymous(), None), Nil))
    )
  }

  def testClassImplicitParam(): Unit = {
    doTest(
      "class C(implicit x: Int)",
      Defn.Class(Nil, Type.Name("C"), Nil, Ctor.Primary(Nil, Name.Anonymous(),
        List(List(Term.Param(List(Mod.Implicit()), Term.Name("x"), Some(Type.Name("Int")), None)))),
        Template(Nil, Nil, Self(Name.Anonymous(), None), Nil))
    )
  }

  def testClassParamOverride(): Unit = {
    doTest(
      "class C(override val x: Int)",
      Defn.Class(Nil, Type.Name("C"), Nil, Ctor.Primary(Nil, Name.Anonymous(),
        List(List(Term.Param(List(Mod.Override(), Mod.ValParam()), Term.Name("x"), Some(Type.Name("Int")), None)))),
        Template(Nil, Nil, Self(Name.Anonymous(), None), Nil))
    )
  }

  def testObjectSimple(): Unit = {
    doTest(
      "object A",
      Defn.Object(Nil, Term.Name("A"),
        Template(Nil, Nil, Self(Name.Anonymous(), None), Nil))
    )
  }

  def testObjectExtends(): Unit = {
    doTest(
      """
        |trait B
        |//start
        |object A extends B
      """.stripMargin,
      Defn.Object(Nil, Term.Name("A"),
        Template(Nil, List(Init(Type.Name("B"), Term.Name("B"), Nil)), Self(Name.Anonymous(), None), Nil))
    )
  }

  def testObjectEarlyDefns(): Unit = {
    doTest(
      """
        |trait B
        |//start
        |object A extends { val x: Int = 2 } with B
      """.stripMargin,
       Defn.Object(Nil, Term.Name("A"),
         Template(List(Defn.Val(Nil, List(Pat.Var(Term.Name("x"))), Some(Type.Name("Int")), Lit.Int(2))),
           List(Init(Type.Name("B"), Term.Name("B"), Nil)), Self(Name.Anonymous(), None), Nil))
    )
  }

  def testObjectSelfType(): Unit = {
    doTest(
      """
        |trait B
        |//start
        |object A { self: B => }
      """.stripMargin,
      Defn.Object(Nil, Term.Name("A"),
        Template(Nil, Nil, Self(Term.Name("self"), Some(Type.Name("B"))), Nil))
    )
  }

  def testTraitExtendsTparams(): Unit = {
    val A = Type.Name("A")
    val B = Type.Name("B")
    val C = Type.Name("C")
    val Foo = Type.Name("Foo")
    val AFoo = Type.Apply(A, List(Foo))

    val init1 = Init(AFoo, Name.Anonymous(), Nil)
    val init2 = Init(Type.Apply(B, List(Type.With(AFoo, C))), Name.Anonymous(), Nil)
    val init3 = Init(C, Name.Anonymous(), Nil)
    val inits = List(init1, init2, init3)
    doTest(
      """
        |trait A[T]
        |trait B[U]
        |trait C
        |//start
        |class Foo extends A[Foo] with B[A[Foo] with C] with C
      """.stripMargin,
      Defn.Class(Nil, Type.Name("Foo"), Nil, Ctor.Primary(Nil, Name.Anonymous(), Nil),
        Template(
          Nil, inits, Self(Name.Anonymous(), None), Nil
        )
      )
    )
  }
}
