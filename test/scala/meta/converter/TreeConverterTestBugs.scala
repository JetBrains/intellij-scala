package scala.meta.converter

import scala.meta.{TreeConverterTestBaseWithLibrary, TreeConverterTestBaseNoLibrary}
import scala.meta._

import scala.collection.immutable.Seq
import scala.{Seq => _}

class TreeConverterTestBugs extends TreeConverterTestBaseWithLibrary {

  def testQualifiedTypes() {
    doTest(
      "object A { class Foo; class Bar extends Foo }",
      Defn.Object(Nil, Term.Name("A"), Template(Nil, Nil, Term.Param(Nil, Name.Anonymous(), None, None), Some(Seq(Defn.Class(Nil, Type.Name("Foo"), Nil, Ctor.Primary(Nil, Ctor.Ref.Name("this"), Nil), Template(Nil, Nil, Term.Param(Nil, Name.Anonymous(), None, None), None)), Defn.Class(Nil, Type.Name("Bar"), Nil, Ctor.Primary(Nil, Ctor.Ref.Name("this"), Nil), Template(Nil, Seq(Ctor.Ref.Name("Foo")), Term.Param(Nil, Name.Anonymous(), None, None), None))))))
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
}
