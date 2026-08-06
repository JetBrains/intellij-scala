package org.jetbrains.plugins.scala.lang.resolveSemanticDb

import junit.framework.TestCase
import org.jetbrains.plugins.scala.lang.resolveSemanticDb.Symbol._

class SymbolTest extends TestCase {
  def testEmpty(): Unit = assertParsed("")

  def testPackageEmpty(): Unit = assertParsed("_empty_/", Package(""))

  def testPackageSingle(): Unit = assertParsed("foo/", Package("foo"))

  def testPackageMultiple(): Unit = assertParsed("foo/bar/", Package("foo.bar"))

//  def testPackageQuotes(): Unit = assertParsed("`&`/", Package("&"))

  def testTypeSingle(): Unit = assertParsed("_empty_/Foo#", Package(""), Type("Foo"))

  def testTypeMultiple(): Unit = assertParsed("_empty_/Foo#Bar#", Package(""), Type("Foo"), Type("Bar"))

  def testTypeQuotes(): Unit = assertParsed("_empty_/`&`#", Package(""), Type("&"))

  def testTermSingle(): Unit = assertParsed("_empty_/Foo.", Package(""), Term("Foo"))

  def testTermMultiple(): Unit = assertParsed("_empty_/Foo.Bar.", Package(""), Term("Foo"), Term("Bar"))

  def testTermQuotes(): Unit = assertParsed("_empty_/`Foo`.", Package(""), Term("Foo"))

  def testTypeAndTerm(): Unit = assertParsed("_empty_/Foo#Bar.", Package(""), Type("Foo"), Term("Bar"))

  def testTermAndType(): Unit = assertParsed("_empty_/Foo.Bar#", Package(""), Term("Foo"), Type("Bar"))

  def testMethodOnType(): Unit = assertParsed("_empty_/Foo#bar().", Package(""), Type("Foo"), Method("bar"))

  def testMethodOnTerm(): Unit = assertParsed("_empty_/Foo.bar().", Package(""), Term("Foo"), Method("bar"))

  def testMethodNumber(): Unit = assertParsed("_empty_/Foo#bar(+1).", Package(""), Type("Foo"), Method("bar", 1))

  def testMethodQuotes(): Unit = assertParsed("_empty_/Foo#`bar`().", Package(""), Type("Foo"), Method("bar"))

  def testConstructor(): Unit = assertParsed("_empty_/Foo#`<init>`().", Package(""), Type("Foo"), Method("<init>"))

  def testParameterOfMethod(): Unit = assertParsed("_empty_/Foo#bar().(x)", Package(""), Type("Foo"), Method("bar"), Parameter("x"))

  def testParameterOfConstructor(): Unit = assertParsed("_empty_/Foo#`<init>`().(x)", Package(""), Type("Foo"), Method("<init>"), Parameter("x"))

  def testParameterQuotes(): Unit = assertParsed("_empty_/Foo#bar().(`x`)", Package(""), Type("Foo"), Method("bar"), Parameter("x"))

  def testTypeParameterOfMethod(): Unit = assertParsed("_empty_/Foo#bar().[A]", Package(""), Type("Foo"), Method("bar"), TypeParameter("A"))

  def testTypeParameterOfConstructor(): Unit = assertParsed("_empty_/Foo#`<init>`().[A]", Package(""), Type("Foo"), Method("<init>"), TypeParameter("A"))

  def testTypeParameterOfType(): Unit = assertParsed("_empty_/Foo#[A]", Package(""), Type("Foo"), TypeParameter("A"))

  def testTypeParameterQuotes(): Unit = assertParsed("_empty_/Foo#[`A`]", Package(""), Type("Foo"), TypeParameter("A"))

  def assertParsed(name: String, symbols: Symbol*): Unit =
    TestCase.assertEquals(symbols.toList, Symbol.parse(name))
}
