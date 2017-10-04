package scala.meta.converter

import scala.collection.immutable.Seq
import scala.meta.TreeConverterTestBaseWithLibrary
import scala.meta._
import scala.{Seq => _}

// tests that require library loading
class TreeConverterDeclTestSlow extends TreeConverterTestBaseWithLibrary {
  def testExistentialType() {
    doTest(
      "def foo(x: Map[T, U] forSome {type T >: Nothing <: Int; type U})",
      Decl.Def(Nil, Term.Name("foo"), Nil, Seq(Seq(Term.Param(Nil, Term.Name("x"),
        Some(Type.Existential(Type.Apply(Type.Name("Map"), Seq(Type.Name("T"), Type.Name("U"))),
          Seq(Decl.Type(Nil, Type.Name("T"), Nil, Type.Bounds(Some(Type.Name("Nothing")),
            Some(Type.Name("Int")))), Decl.Type(Nil, Type.Name("U"), Nil, Type.Bounds(None, None))))), None))),
        Type.Name("Unit"))
    )
  }


  def testDefFunctionalTypeParam() {
    doTest(
      "def f(a: Int => Any)",
      Decl.Def(Nil, Term.Name("f"), Nil,
        List(List(Term.Param(Nil, Term.Name("a"),
          Some(Type.Function(List(Type.Name("Int")), Type.Name("Any"))), None))), Type.Name("Unit"))
    )
  }



  def testDefTupleFunctionalTypeParam() {
    doTest(
      "def f(a: (Int, Any) => Any)",
      Decl.Def(Nil, Term.Name("f"), Nil,
        List(List(Term.Param(Nil, Term.Name("a"),
          Some(Type.Function(List(Type.Name("Int"), Type.Name("Any")), Type.Name("Any"))), None))), Type.Name("Unit"))
    )
  }
}
