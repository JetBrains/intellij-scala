package org.jetbrains.plugins.scala.meta


import scala.meta.internal.ast._

class TreeConverterDefnTest extends  TreeConverterTestBase {

  def testValInit() {
    doTest(
      "val x = 2",
      Defn.Val(Nil, Pat.Var.Term(Term.Name("x")) :: Nil, None, Lit.Int(2))
    )
  }

  def testVarInit(): Unit = {
    doTest(
      "var x = 2",
      Defn.Var(Nil, Pat.Var.Term(Term.Name("x")) :: Nil, None, Some(Lit.Int(2)))
    )
  }

  def testMultiValInit(): Unit = {
    doTest(
      "val x, y = 2",
      Defn.Val(Nil, Pat.Var.Term(Term.Name("x")) :: Pat.Var.Term(Term.Name("y")) :: Nil,
        None, Lit.Int(2))
    )
  }

  def testTypedValInit(): Unit = {
    doTest(
      "val x: Int = 2",
      Defn.Val(Nil, Pat.Var.Term(Term.Name("x")) :: Nil, Some(Type.Name("Int")), Lit.Int(2))
    )
  }

  def testInitializeVarEmpty(): Unit = {
    doTest(
      "var x: Int = _",
      Defn.Var(Nil, Pat.Var.Term(Term.Name("x")) :: Nil, Some(Type.Name("Int")), None)
    )
  }

  def testDef(): Unit = {
    doTest(
      "def f = 2",
      Defn.Def(Nil, Term.Name("f"), Nil, Nil, None, Lit.Int(2))
    )
  }
  
  def testDefTypeBoundsArg() {
    doTest(
      "def x[A <: Any] = 2",
      Defn.Def(Nil, Term.Name("x"),
        Type.Param(Nil, Type.Name("A"), Nil, Type.Bounds(None, Some(Type.Name("Any"))), Nil, Nil) :: Nil,
        Nil, None, Lit.Int(2))
    )
  }
  
  def testDefViewTypeBounds() {
    doTest(
      "def x[A <% Any] = 2",
      Defn.Def(Nil, Term.Name("x"),
        Type.Param(Nil, Type.Name("A"), Nil, Type.Bounds(None, None), Type.Name("Any") :: Nil, Nil) :: Nil,
        Nil, None, Lit.Int(2))
    )
  }
  
  def testDefContextBounds() {
    doTest(
      "def x[A: Any] = 2",
      Defn.Def(Nil, Term.Name("x"),
        Type.Param(Nil, Type.Name("A"), Nil, Type.Bounds(None, None), Nil, Type.Name("Any") :: Nil) :: Nil,
        Nil, None, Lit.Int(2))
    )
  }
  
  def testDefImplicitParam() {
    doTest(
      "def f(a: Int)(implicit b: Int) = a + b",
      Defn.Def(Nil, Term.Name("f"), Nil,
        (Term.Param(Nil, Term.Name("a"), Some(Type.Name("Int")), None) :: Nil) ::
          (Term.Param(Mod.Implicit() :: Nil, Term.Name("b"), Some(Type.Name("Int")), None) :: Nil) :: Nil, None,
        Term.ApplyInfix(Term.Name("a"), Term.Name("+"), Nil, Term.Name("b") :: Nil))
    )
  }
  
  def testDefBrackets() {
    doTest(
      "def proc { return 42 }",
      Defn.Def(Nil, Term.Name("proc"), Nil, Nil, Some(Type.Name("Unit")), Term.Block(Term.Return(Lit.Int(42)) :: Nil))
    )
  }

}
