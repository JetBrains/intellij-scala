package scala.meta.converter

import scala.meta.{TreeConverterTestBaseNoLibrary, _}

class TreeConverterLiteralTest extends  TreeConverterTestBaseNoLibrary {
  
  def testTrue(): Unit = {
    doTest(
      "true",
      Lit.Boolean(true)
    )
  }
  
  def testFalse(): Unit = {
    doTest(
      "false",
      Lit.Boolean(false)
    )
  }
  
  def testInt(): Unit = {
    doTest(
      "42",
      Lit.Int(42)
    )
  }
  
  def testLong(): Unit = {
    doTest(
      "42L",
      Lit.Long(42L)
    )
  }
  
  def testDouble(): Unit = {
    doTest(
      "42.0",
      Lit.Double(42.0)
    )
  }
  
  def testFloat(): Unit = {
    doTest(
      "42.0f",
      Lit.Float(42.0f)
    )
  }
  
  def testChar(): Unit = {
    doTest(
      "'c'",
      Lit.Char('c')
    )
  }
  
  def testString(): Unit = {
    doTest(
      "\"foo\"",
      Lit.String("foo")
    )
  }

  def testSymbol(): Unit = {
    doTest(
      "'foo'",
      Term.Apply(Term.Select(Term.Name("scala"), Term.Name("Symbol")), List(Lit.String("'foo")))
    )
  }
  
  def testNull(): Unit = {
    doTest(
      "null",
      Lit.Null()
    )
  }

  def testUnit(): Unit = {
    doTest(
      "()",
      Lit.Unit()
    )
  }

}
