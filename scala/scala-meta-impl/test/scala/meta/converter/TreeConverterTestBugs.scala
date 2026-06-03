package scala.meta.converter

import scala.meta.{TreeConverterTestBaseWithLibrary, _}

class TreeConverterTestBugs extends TreeConverterTestBaseWithLibrary {

  def testQualifiedTypes(): Unit = {
    doTest(
      "object A { class Foo; class Bar extends Foo }",
      Defn.Object(Nil, Term.Name("A"), Template(Nil, Nil, Self(Name.Anonymous(), None),
        List(Defn.Class(Nil, Type.Name("Foo"), Nil, Ctor.Primary(Nil, Name.Anonymous(), Nil),
          Template(Nil, Nil, Self(Name.Anonymous(), None), Nil)),
          Defn.Class(Nil, Type.Name("Bar"), Nil, Ctor.Primary(Nil, Name.Anonymous(), Nil),
            Template(Nil, List(Init(Type.Name("Foo"), Term.Name("Foo"), Nil)), Self(Name.Anonymous(), None), Nil)))))
    )
  }

  def testThisQualifier(): Unit = {
    doTest(
      "trait Foo { def foo = Foo.this.hashCode }",
      Defn.Trait(Nil, Type.Name("Foo"), Nil, Ctor.Primary(Nil, Name.Anonymous(), Nil),
        Template(Nil, Nil, Self(Name.Anonymous(), None),
          List(Defn.Def(Nil, Term.Name("foo"), Nil, Nil, None,
            Term.Select(Term.This(Name.Indeterminate("Foo")), Term.Name("hashCode"))))))
    )
  }

  def testParametrizedReturnTypeElement(): Unit = {
    doTest(
      "trait Foo[A] { def fooOpToId[A](fooOp: FooOp[A]): Id[A] = ??? }",
      Defn.Trait(Nil, Type.Name("Foo"), List(Type.Param(Nil, Type.Name("A"), Nil, Type.Bounds(None, None), Nil, Nil)),
        Ctor.Primary(Nil, Name.Anonymous(), Nil), Template(Nil, Nil, Self(Name.Anonymous(), None),
          List(Defn.Def(Nil, Term.Name("fooOpToId"), List(Type.Param(Nil, Type.Name("A"), Nil, Type.Bounds(None, None),
            Nil, Nil)), List(List(Term.Param(Nil, Term.Name("fooOp"), Some(Type.Apply(Type.Name("FooOp"),
            List(Type.Name("A")))), None))), Some(Type.Apply(Type.Name("Id"), List(Type.Name("A")))), Term.Name("???")))))
    )
  }

  def testSCL11098(): Unit = {
    doTest(
      "val x: Foo[Int Or String] = 2",
      Defn.Val(Nil, Pat.Var(Term.Name("x")) :: Nil, Some(Type.Apply(Type.Name("Foo"),
        List(Type.ApplyInfix(Type.Name("Int"), Type.Name("Or"), Type.Name("String"))))), Lit.Int(2))
    )
  }

  // object with apply method has incorrect name
  def testSCL11024(): Unit = {
    doTest(
      "object Foo { def apply() = 42 }",
      Defn.Object(Nil, Term.Name("Foo"), Template(Nil, Nil, Self(Name.Anonymous(), None),
        List(Defn.Def(Nil, Term.Name("apply"), Nil, List(List()), None, Lit.Int(42)))))
    )
  }

  // Meta annotation expansion improperly handles destructuring
  def testSCL12523(): Unit = {
    doTest(
      "val (a, b) = (42, 1337)",
      Defn.Val(Nil, List(Pat.Tuple(List(Pat.Var(Term.Name("a")), Pat.Var(Term.Name("b"))))),
        None, Term.Tuple(List(Lit.Int(42), Lit.Int(1337))))
    )
  }
}
