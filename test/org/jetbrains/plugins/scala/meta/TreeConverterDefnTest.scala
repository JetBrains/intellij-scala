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

}
