package scala.meta.converter

import scala.meta.TreeConverterTestBaseNoLibrary
import scala.meta._

import scala.collection.immutable.Seq

class TreeConverterLiteralTest extends  TreeConverterTestBaseNoLibrary {
  
  def testTrue() {
    doTest(
      "true",
      Lit(true)
    )
  }
  
  def testFalse() {
    doTest(
      "false",
      Lit(false)
    )
  }
  
  def testInt() {
    doTest(
      "42",
      Lit(42)
    )
  }
  
  def testLong() {
    doTest(
      "42L",
      Lit(42L)
    )
  }
  
  def testDouble() {
    doTest(
      "42.0",
      Lit(42.0)
    )
  }
  
  def testFloat() {
    doTest(
      "42.0f",
      Lit(42.0f)
    )
  }
  
  def testChar() {
    doTest(
      "'c'",
      Lit('c')
    )
  }
  
  def testString() {
    doTest(
      "\"foo\"",
      Lit("foo")
    )
  }

  def testSymbol() {
    doTest(
      "'foo'",
      Term.Apply(Term.Select(Term.Name("scala"), Term.Name("Symbol")), Seq(Lit("foo")))
    )
  }
  
  def testNull() {
    doTest(
      "null",
      Lit(null)
    )
  }

  def testUnit() {
    doTest(
      "()",
      Lit(())
    )
  }

}
