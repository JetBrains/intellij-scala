package scala.meta.converter

import scala.meta.{TreeConverterTestBaseWithLibrary, _}

class TreeConverterDefnTest extends  TreeConverterTestBaseWithLibrary {

  def testValInit(): Unit = {
    doTest(
      "val x = 2",
      Defn.Val(Nil, Pat.Var(Term.Name("x")) :: Nil, None, Lit.Int(2))
    )
  }

  def testVarInit(): Unit = {
    doTest(
      "var x = 2",
      Defn.Var(Nil, Pat.Var(Term.Name("x")) :: Nil, None, Some(Lit.Int(2)))
    )
  }

  def testMultiValInit(): Unit = {
    doTest(
      "val x, y = 2",
      Defn.Val(Nil, Pat.Var(Term.Name("x")) :: Pat.Var(Term.Name("y")) :: Nil,
        None, Lit.Int(2))
    )
  }

  def testTypedValInit(): Unit = {
    doTest(
      "val x: Int = 2",
      Defn.Val(Nil, Pat.Var(Term.Name("x")) :: Nil, Some(Type.Name("Int")), Lit.Int(2))
    )
  }

  def testInitializeVarEmpty(): Unit = {
    doTest(
      "var x: Int = _",
      Defn.Var(Nil, Pat.Var(Term.Name("x")) :: Nil, Some(Type.Name("Int")), None)
    )
  }

  def testDef(): Unit = {
    doTest(
      "def f = 2",
      Defn.Def(Nil, Term.Name("f"), Nil, Nil, None, Lit.Int(2))
    )
  }
  
  def testDefTypeBoundsArg(): Unit = {
    doTest(
      "def x[A <: Any] = 2",
      Defn.Def(Nil, Term.Name("x"),
        Type.Param(Nil, Type.Name("A"), Nil, Type.Bounds(None, Some(Type.Name("Any"))), Nil, Nil) :: Nil,
        Nil, None, Lit.Int(2))
    )
  }
  
  def testDefViewTypeBounds(): Unit = {
    doTest(
      "def x[A <% Any] = 2",
      Defn.Def(Nil, Term.Name("x"),
        Type.Param(Nil, Type.Name("A"), Nil, Type.Bounds(None, None), Type.Name("Any") :: Nil, Nil) :: Nil,
        Nil, None, Lit.Int(2))
    )
  }
  
  def testDefContextBounds(): Unit = {
    doTest(
      "def x[A: Any] = 2",
      Defn.Def(Nil, Term.Name("x"),
        Type.Param(Nil, Type.Name("A"), Nil, Type.Bounds(None, None), Nil, Type.Name("Any") :: Nil) :: Nil,
        Nil, None, Lit.Int(2))
    )
  }
  
  def testDefImplicitParam(): Unit = {
    doTest(
      "def f(a: Int)(implicit b: Int) = b",
      Defn.Def(Nil, Term.Name("f"), Nil,
        (Term.Param(Nil, Term.Name("a"), Some(Type.Name("Int")), None) :: Nil) ::
          (Term.Param(Mod.Implicit() :: Nil, Term.Name("b"), Some(Type.Name("Int")), None) :: Nil) :: Nil, None, Term.Name("b"))
    )
  }
  
  def testDefBrackets(): Unit = {
    doTest(
      "def proc { return 42 }",
      Defn.Def(Nil, Term.Name("proc"), Nil, Nil, None, Term.Block(Term.Return(Lit.Int(42)) :: Nil))
    )
  }

  def testTypeAliasDef(): Unit = {
    doTest(
      "type A = Any",
      Defn.Type(Nil, Type.Name("A"), Nil, Type.Name("Any"))
    )
  }

  def testTypeAliasApply(): Unit = {
    doTest(
    """
      |trait A[+T]
      |//start
      |type F[T] = A[T]
    """.stripMargin,
      Defn.Type(Nil, Type.Name("F"),
        Type.Param(Nil, Type.Name("T"), Nil, Type.Bounds(None, None), Nil, Nil) :: Nil,
        Type.Apply(Type.Name("A"), Type.Name("T") :: Nil))
    )
  }

  def testManyStatements(): Unit = {
    doTest(
      "def f = {f(); f(); 42}",
      Defn.Def(Nil, Term.Name("f"), Nil, Nil, None, Term.Block(List(Term.Apply(Term.Name("f"), Nil), Term.Apply(Term.Name("f"), Nil), Lit.Int(42))))
    )
  }

  def testDefaultValue(): Unit = {
    doTest(
      "def f(a: Int = 42) = a",
      Defn.Def(Nil, Term.Name("f"), Nil, List(List(Term.Param(Nil, Term.Name("a"), Some(Type.Name("Int")), Some(Lit.Int(42))))), None, Term.Name("a"))
    )
  }
}
