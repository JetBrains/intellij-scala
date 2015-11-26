package scala.meta.converter

import scala.meta.TreeConverterTestBaseWithLibrary
import scala.meta.internal.ast._
import scala.collection.immutable.Seq
import scala.{Seq => _}

class TreeConverterDeclTestSlow  extends  TreeConverterTestBaseWithLibrary {
def testExistentialType() {
  doTest(
    "def foo(x: Map[T, U] forSome {type T >: Nothing <: A; type U})",
    Decl.Def(Nil, Term.Name("foo"), Nil, Seq(Seq(Term.Param(Nil, Term.Name("x"), Some(Type.Existential(Type.Apply(Type.Name("Map"), Seq(Type.Name("T"), Type.Name("U"))), Seq(Decl.Type(Nil, Type.Name("T"), Nil, Type.Bounds(Some(Type.Name("Nothing")), Some(Type.Name("A")))), Decl.Type(Nil, Type.Name("U"), Nil, Type.Bounds(None, None))))), None))), Type.Name("Unit"))
  )
}
}
