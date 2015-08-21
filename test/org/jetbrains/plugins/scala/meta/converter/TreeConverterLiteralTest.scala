package org.jetbrains.plugins.scala.meta.converter

import org.jetbrains.plugins.scala.meta.TreeConverterTestBaseNoLibrary

import scala.meta.internal.ast._

class TreeConverterLiteralTest extends  TreeConverterTestBaseNoLibrary {
  
  def testTrue() {
    doTest(
      "true",
      Lit.Bool(true)
    )
  }
  
  def testFalse() {
    doTest(
      "false",
      Lit.Bool(false)
    )
  }
  
  def testInt() {
    doTest(
      "42",
      Lit.Int(42)
    )
  }
  
  def testLong() {
    doTest(
      "42L",
      Lit.Long(42L)
    )
  }
  
  def testDouble() {
    doTest(
      "42.0",
      Lit.Double(42.0)
    )
  }
  
  def testFloat() {
    doTest(
      "42.0f",
      Lit.Float(42.0f)
    )
  }
  
  def testChar() {
    doTest(
      "'c'",
      Lit.Char('c')
    )
  }
  
  def testString() {
    doTest(
      "\"foo\"",
      Lit.String("foo")
    )
  }

  def testSymbol() {
    doTest(
      "'foo'",
      Lit.Symbol('foo)
    )
  }
  
  def testNull() {
    doTest(
      "null",
      Lit.Null()
    )
  }

  def testUnit() {
    doTest(
      "()",
      Lit.Unit()
    )
  }

}
