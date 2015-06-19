package org.jetbrains.plugins.scala.meta

import scala.meta.internal.ast._

class TreeConverterExprTest extends TreeConverterTestBase {

  def testIf() {
    doTest(
      "if (true) 42",
      Term.If(Lit.Bool(value = true), Lit.Int(42), Lit.Unit())
    )
  }
  
  def testIfElse() {
    doTest(
      "if (false) 42 else 0",
      Term.If(Lit.Bool(value = false), Lit.Int(42), Lit.Int(0))
    )
  }
  
  def testIfElseIfElse() {
    doTest(
      "if (true) 42 else if (false) 999 else 0",
      Term.If(Lit.Bool(value = true), Lit.Int(42), Term.If(Lit.Bool(value = false), Lit.Int(999), Lit.Int(0)))
    )
  }
  
}
