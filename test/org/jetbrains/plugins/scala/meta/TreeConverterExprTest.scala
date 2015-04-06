package org.jetbrains.plugins.scala.meta

import scala.meta.internal.ast._

class TreeConverterExprTest extends TreeConverterTestBase {

  def testIf() {
    doTest(
      "if (f()) 42",
      Term.If(Term.Apply(Term.Name("f"), Nil), Lit.Int(42), Lit.Unit())
    )
  }
  
  def testIfElse() {
    doTest(
      "if (f()) 42 else 0",
      Term.If(Term.Apply(Term.Name("f"), Nil), Lit.Int(42), Lit.Int(0))
    )
  }
  
  def testIfElseIfElse() {
    doTest(
      "if (f()) 42 else if (g()) 999 else 0",
      Term.If(Term.Apply(Term.Name("f"), Nil), Lit.Int(42), Term.If(Term.Apply(Term.Name("g"), Nil), Lit.Int(999), Lit.Int(0)))
    )
  }
  
}
