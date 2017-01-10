package scala.meta.converter

import scala.meta.{TreeConverterTestBaseWithLibrary, TreeConverterTestBaseNoLibrary}
import scala.meta._

import scala.collection.immutable.Seq
import scala.{Seq => _}

class TreeConverterTestBugs extends TreeConverterTestBaseWithLibrary {

  def testQualifiedTypes() {
    doTest(
      "object A { class Foo; class Bar extends Foo }",
      Defn.Object(Nil, Term.Name("A"), Template(Nil, Nil, Term.Param(Nil, Name.Anonymous(), None, None),
        Some(Seq(Defn.Class(Nil, Type.Name("Foo"), Nil, Ctor.Primary(Nil, Ctor.Ref.Name("this"), Nil),
          Template(Nil, Nil, Term.Param(Nil, Name.Anonymous(), None, None), None)),
          Defn.Class(Nil, Type.Name("Bar"), Nil, Ctor.Primary(Nil, Ctor.Ref.Name("this"), Nil),
            Template(Nil, Seq(Term.Apply(Ctor.Ref.Name("Foo"), Nil)), Term.Param(Nil, Name.Anonymous(), None, None), None))))))
    )
  }

  def testThisQualifier() {
    doTest(
      "trait Foo { def foo = Foo.this.hashCode }",
      Defn.Trait(Nil, Type.Name("Foo"), Nil, Ctor.Primary(Nil, Ctor.Ref.Name("this"), Nil),
        Template(Nil, Nil, Term.Param(Nil, Name.Anonymous(), None, None),
          Some(Seq(Defn.Def(Nil, Term.Name("foo"), Nil, Nil, None,
            Term.Select(Term.This(Name.Indeterminate("Foo")), Term.Name("hashCode")))))))
    )
  }

  def testParametrizedReturnTypeElement(): Unit = {
    doTest(
      "trait Foo[A] { def fooOpToId[A](fooOp: FooOp[A]): Id[A] = ??? }",
      Defn.Trait(Nil, Type.Name("Foo"), Seq(Type.Param(Nil, Type.Name("A"), Nil, Type.Bounds(None, None), Nil, Nil)),
        Ctor.Primary(Nil, Ctor.Ref.Name("this"), Nil), Template(Nil, Nil, Term.Param(Nil, Name.Anonymous(), None, None),
          Some(Seq(Defn.Def(Nil, Term.Name("fooOpToId"), Seq(Type.Param(Nil, Type.Name("A"), Nil, Type.Bounds(None, None),
            Nil, Nil)), Seq(Seq(Term.Param(Nil, Term.Name("fooOp"), Some(Type.Apply(Type.Name("FooOp"),
            Seq(Type.Name("A")))), None))), Some(Type.Apply(Type.Name("Id"), Seq(Type.Name("A")))), Term.Name("???"))))))
    )
  }

  def testSCL11098(): Unit = {
    doTest(
      "val x: Foo[Int Or String] = 2",
      Defn.Val(Nil, Pat.Var.Term(Term.Name("x")) :: Nil, Some(Type.Apply(Type.Name("Foo"),
        Seq(Type.ApplyInfix(Type.Name("Int"), Type.Name("Or"), Type.Name("String"))))), Lit(2))
    )
  }
}
