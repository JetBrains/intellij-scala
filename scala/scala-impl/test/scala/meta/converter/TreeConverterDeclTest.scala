package scala.meta.converter

import scala.meta.TreeConverterTestBaseNoLibrary
import scala.meta._


class TreeConverterDeclTest extends TreeConverterTestBaseNoLibrary {

  def testVal(): Unit = {
    doTest(
      "val x,y: Int",
      Decl.Val(Nil, List(Pat.Var(Term.Name("x")), Pat.Var(Term.Name("y"))), Type.Name("Int"))
    )
  }

  def testVar(): Unit = {
    doTest(
      "var x: Int",
      Decl.Var(Nil, List(Pat.Var(Term.Name("x"))), Type.Name("Int"))
    )
  }


  def testMultiVal(): Unit = {
    doTest(
      "val x, y: Int",
      Decl.Val(Nil, List(Pat.Var(Term.Name("x")), Pat.Var(Term.Name("y"))), Type.Name("Int"))
    )
  }

  def testMultiVar(): Unit = {
    doTest(
      "var x, y: Int",
      Decl.Var(Nil, List(Pat.Var(Term.Name("x")), Pat.Var(Term.Name("y"))), Type.Name("Int"))
    )
  }

  def testTypeT(): Unit = {
    doTest(
      "type T",
      Decl.Type(Nil, Type.Name("T"), Nil, Type.Bounds(None, None))
    )
  }

  def testTypeUpperBound(): Unit = {
    doTest(
      "type T <: Any",
      Decl.Type(Nil, Type.Name("T"), Nil, Type.Bounds(None, Some(Type.Name("Any"))))
    )
  }

  def testTypeLowerBound(): Unit = {
    doTest(
      "type T >: Any",
      Decl.Type(Nil, Type.Name("T"), Nil, Type.Bounds(Some(Type.Name("Any")), None))
    )
  }

  def testBothTypeBounds(): Unit = {
    doTest(
      "type T >: Any <: Int",
      Decl.Type(Nil, Type.Name("T"), Nil, Type.Bounds(Some(Type.Name("Any")), Some(Type.Name("Int"))))
    )
  }

  def testParametrizedType(): Unit = {
    doTest(
      "type F[T]",
      Decl.Type(Nil, Type.Name("F"),
        Type.Param(Nil, Type.Name("T"), Nil, Type.Bounds(None, None), Nil, Nil) :: Nil,
        Type.Bounds(None, None))
    )
  }

  def testParametrizedAnonType(): Unit = {
    doTest(
      "type F[_]",
      Decl.Type(Nil, Type.Name("F"),
        Type.Param(Nil, Name.Anonymous(), Nil, Type.Bounds(None, None), Nil, Nil) :: Nil,
        Type.Bounds(None, None))
    )
  }

  def testParametrizedWithUpperBoundType(): Unit = {
    doTest(
      "type F[T <: Any]",
      Decl.Type(Nil, Type.Name("F"),
        Type.Param(Nil, Type.Name("T"), Nil, Type.Bounds(None, Some(Type.Name("Any"))), Nil, Nil) :: Nil,
        Type.Bounds(None, None))
    )
  }

  def testCovariantType(): Unit = {
    doTest(
      "type F[+T]",
      Decl.Type(Nil, Type.Name("F"),
        Type.Param(Mod.Covariant() :: Nil, Type.Name("T"), Nil, Type.Bounds(None, None), Nil, Nil) :: Nil,
        Type.Bounds(None, None))
    )
  }

  def testContravariantType(): Unit = {
    doTest(
      "type F[-T]",
      Decl.Type(Nil, Type.Name("F"),
        Type.Param(Mod.Contravariant() :: Nil, Type.Name("T"), Nil, Type.Bounds(None, None), Nil, Nil) :: Nil,
        Type.Bounds(None, None))
    )
  }

  def testDefNoReturnType(): Unit = {
    doTest(
      "def f",
      Decl.Def(Nil, Term.Name("f"), Nil, Nil, Type.Name("Unit"))
    )
  }

  def testDefWithReturnType(): Unit = {
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

  def testDefVararg(): Unit = {
    doTest(
      "def f (a: Int*)",
      Decl.Def(Nil, Term.Name("f"), Nil, List(List(Term.Param(Nil, Term.Name("a"), Some(Type.Repeated(Type.Name("Int"))), None))), Type.Name("Unit"))
    )
  }

  def testImplicitArgument(): Unit = {
    doTest(
      "def f(implicit x: Int)",
      Decl.Def(Nil, Term.Name("f"), Nil,
        (Term.Param(Mod.Implicit() :: Nil, Term.Name("x"), Some(Type.Name("Int")), None) :: Nil) :: Nil,
        Type.Name("Unit"))
    )
  }

  def testDefTypeArgs(): Unit = {
    doTest(
      "def f[T]: T",
      Decl.Def(Nil, Term.Name("f"),
        Type.Param(Nil, Type.Name("T"), Nil, Type.Bounds(None, None), Nil, Nil) :: Nil,
        Nil, Type.Name("T"))
    )
  }
  
  def testLocalDeclarations(): Unit = {
    doTest(
      "def f = { val x = 42 }",
      Defn.Def(Nil, Term.Name("f"), Nil, Nil, None, Term.Block(List(Defn.Val(Nil, List(Pat.Var(Term.Name("x"))), None, Lit.Int(42)))))
    )
  }
}
