package org.jetbrains.plugins.scala.meta

import scala.meta.internal.ast._

class TreeConverterImportTest extends TreeConverterTestBase {

  def testImportSimple() {
    doTest(
      "import scala.Any",
      Import(List(Import.Clause(Term.Name("scala"), List(Import.Selector.Name(Name.Indeterminate("Any"))))))
    )
  }
  
  def testImportMultipleClauses() {
    doTest(
      "import scala.{Any, AnyRef}",
      Import(List(Import.Clause(Term.Name("scala"), List(Import.Selector.Name(Name.Indeterminate("Any")), Import.Selector.Name(Name.Indeterminate("AnyRef"))))))
    )
  }

  def testImportWildCard() {
    doTest(
      "import scala._",
      Import(List(Import.Clause(Term.Name("scala"), List(Import.Selector.Wildcard()))))
    )
  }
  
  def testImportRename() {
    doTest(
      "import scala.{Any => asdf}",
      Import(List(Import.Clause(Term.Name("scala"), List(Import.Selector.Rename(Name.Indeterminate("Any"), Name.Indeterminate("asdf"))))))
    )
  }
  
  def testImportSelector() {
    doTest(
      "import foo.bar.baz",
      Import(List(Import.Clause(Term.Select(Term.Name("foo"), Term.Name("bar")), List(Import.Selector.Name(Name.Indeterminate("baz"))))))
    )
  }
  
  def testImportSuper() {
    doTest(
      "import super.foo.bar",
      Import(List(Import.Clause(Term.Select(Term.Super(Name.Anonymous(), Name.Anonymous()), Term.Name("foo")), List(Import.Selector.Name(Name.Indeterminate("bar"))))))
    )
  }

  def testImportThis() {
    doTest(
      "import this.foo.bar",
      Import(List(Import.Clause(Term.Select(Term.This(Name.Anonymous()), Term.Name("foo")), List(Import.Selector.Name(Name.Indeterminate("bar"))))))
    )
  }

  def testImportMultiple() {
    doTest(
      "import scala.Any, Any.foo",
      Import(List(Import.Clause(Term.Name("scala"), List(Import.Selector.Name(Name.Indeterminate("Any")))), Import.Clause(Term.Name("Any"), List(Import.Selector.Name(Name.Indeterminate("foo"))))))
    )
  }
  
  def testUnImport() {
    doTest(
      "import foo.{bar => _}",
       Import(List(Import.Clause(Term.Name("foo"), List(Import.Selector.Unimport(Name.Indeterminate("bar"))))))
    )
  }

  def testImportAllExcept() {
    doTest(
      "import foo.{bar => _, _}",
      Import(List(Import.Clause(Term.Name("foo"), List(Import.Selector.Unimport(Name.Indeterminate("bar")), Import.Selector.Wildcard()))))
    )
  }

  def testMixedUnImport() {
    doTest(
      "import foo.{baz, bar => _, _}",
      Import(List(Import.Clause(Term.Name("foo"), List(Import.Selector.Name(Name.Indeterminate("baz")), Import.Selector.Unimport(Name.Indeterminate("bar")), Import.Selector.Wildcard()))))
    )
  }
}
