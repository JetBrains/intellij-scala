package org.jetbrains.plugins.scala.meta

import org.jetbrains.plugins.scala.base.SimpleTestCase

import scala.meta.internal.ast._


class TreeConverterDeclTest extends SimpleTestCase with TreeConverterTestBase {

  def testVal() {
    doTest(
      "val x,y: Int",
      Decl.Val(Nil, List(Pat.Var.Term(Term.Name("x")), Pat.Var.Term(Term.Name("y"))), Type.Name("Int"))
    )
  }

  def testVar() {
    doTest(
      "var x: Int",
      Decl.Var(Nil, List(Pat.Var.Term(Term.Name("x"))), Type.Name("Int"))
    )
  }


  def testMultiVal() {
    doTest(
      "val x, y: Int",
      Decl.Val(Nil, List(Pat.Var.Term(Term.Name("x")), Pat.Var.Term(Term.Name("y"))), Type.Name("Int"))
    )
  }

  def testMultiVar() {
    doTest(
      "var x, y: Int",
      Decl.Var(Nil, List(Pat.Var.Term(Term.Name("x")), Pat.Var.Term(Term.Name("y"))), Type.Name("Int"))
    )
  }

  def testTypeT(): Unit = {
    doTest(
      "type T",
      Decl.Type(Nil, Type.Name("T"), Nil, Type.Bounds(None, None))
    )
  }

  def testTypeUpperBound() {
    doTest(
      "type T <: Any",
      Decl.Type(Nil, Type.Name("T"), Nil, Type.Bounds(None, Some(Type.Name("Any"))))
    )
  }

  def testTypeLowerBound() {
    doTest(
      "type T >: Any",
      Decl.Type(Nil, Type.Name("T"), Nil, Type.Bounds(Some(Type.Name("Any")), None))
    )
  }

  def testBothTypeBounds() {
    doTest(
      "type T >: Any <: Int",
      Decl.Type(Nil, Type.Name("T"), Nil, Type.Bounds(Some(Type.Name("Any")), Some(Type.Name("Int"))))
    )
  }

  def testParametrizedType() {
    doTest(
      "type F[T]",
      Decl.Type(Nil, Type.Name("F"),
        Type.Param(Nil, Type.Name("T"), Nil, Type.Bounds(None, None), Nil, Nil) :: Nil,
        Type.Bounds(None, None))
    )
  }

  def testParametrizedAnonType() {
    doTest(
      "type F[_]",
      Decl.Type(Nil, Type.Name("F"),
        Type.Param(Nil, Name.Anonymous(), Nil, Type.Bounds(None, None), Nil, Nil) :: Nil,
        Type.Bounds(None, None))
    )
  }

  def testParametrizedWithUpperBoundType() {
    doTest(
      "type F[T <: Any]",
      Decl.Type(Nil, Type.Name("F"),
        Type.Param(Nil, Type.Name("T"), Nil, Type.Bounds(None, Some(Type.Name("Any"))), Nil, Nil) :: Nil,
        Type.Bounds(None, None))
    )
  }

  def testCovariantType() {
    doTest(
      "type F[+T]",
      Decl.Type(Nil, Type.Name("F"),
        Type.Param(Mod.Covariant() :: Nil, Type.Name("T"), Nil, Type.Bounds(None, None), Nil, Nil) :: Nil,
        Type.Bounds(None, None))
    )
  }

  def testContravariantType() {
    doTest(
      "type F[-T]",
      Decl.Type(Nil, Type.Name("F"),
        Type.Param(Mod.Contravariant() :: Nil, Type.Name("T"), Nil, Type.Bounds(None, None), Nil, Nil) :: Nil,
        Type.Bounds(None, None))
    )
  }

  def testDefNoReturnType() {
    doTest(
      "def f",
      Decl.Def(Nil, Term.Name("f"), Nil, Nil, Type.Name("Unit"))
    )
  }

  def testDefWithReturnType() {
    doTest(
      "def f: Int",
      Decl.Def(Nil, Term.Name("f"), Nil, Nil, Type.Name("Int"))
    )
  }

  def testDefOneParameter(): Unit = {
    doTest(
      "def f(x: Int)",
      Decl.Def(Nil, Term.Name("f"), Nil,
        (Term.Param(Nil, Term.Name("x"), Some(Type.Name("Int")), None) :: Nil) :: Nil,
        Type.Name("Unit"))
    )
  }

  def testDefManyParameters(): Unit = {
    doTest(
      "def f(x: Int, y: Int)",
      Decl.Def(Nil, Term.Name("f"), Nil,
        (Term.Param(Nil, Term.Name("x"), Some(Type.Name("Int")), None) :: Term.Param(Nil, Term.Name("y"), Some(Type.Name("Int")), None)  :: Nil) :: Nil,
        Type.Name("Unit"))
    )
  }

  def testDefMiltiParameterList(): Unit = {
    doTest(
      "def f(x: Int)(y: Int)",
      Decl.Def(Nil, Term.Name("f"), Nil,
        (Term.Param(Nil, Term.Name("x"), Some(Type.Name("Int")), None) :: Nil) ::
          (Term.Param(Nil, Term.Name("y"), Some(Type.Name("Int")), None) :: Nil)
          ::  Nil,
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

  def testDefVararg() {
    doTest(
      "def f (a: Int*)",
      Decl.Def(Nil, Term.Name("f"), Nil, List(List(Term.Param(Nil, Term.Name("a"), Some(Type.Arg.Repeated(Type.Name("Int"))), None))), Type.Name("Unit"))
    )
  }
}
