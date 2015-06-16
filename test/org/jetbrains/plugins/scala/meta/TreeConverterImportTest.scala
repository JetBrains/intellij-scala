package org.jetbrains.plugins.scala.meta

import scala.meta.internal.ast._

class TreeConverterImportTest extends TreeConverterTestBaseWithLibrary {

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
      "import scala.collection.immutable",
      Import(List(Import.Clause(Term.Select(Term.Name("scala"), Term.Name("collection")), List(Import.Selector.Name(Name.Indeterminate("immutable"))))))
    )
  }

  def testImportMultiple() {
    doTest(
      "import scala.collection, collection.immutable",
      Import(List(Import.Clause(Term.Name("scala"), List(Import.Selector.Name(Name.Indeterminate("collection")))), Import.Clause(Term.Name("collection"), List(Import.Selector.Name(Name.Indeterminate("immutable"))))))
    )
  }
  
  def testUnImport() {
    doTest(
      "import scala.{collection => _}",
       Import(List(Import.Clause(Term.Name("scala"), List(Import.Selector.Unimport(Name.Indeterminate("collection"))))))
    )
  }

  def testImportAllExcept() {
    doTest(
      "import scala.{collection => _, _}",
      Import(List(Import.Clause(Term.Name("scala"), List(Import.Selector.Unimport(Name.Indeterminate("collection")), Import.Selector.Wildcard()))))
    )
  }

  def testMixedUnImport() {
    doTest(
      "import scala.{collection, BigInt => _, _}",
      Import(List(Import.Clause(Term.Name("scala"), List(Import.Selector.Name(Name.Indeterminate("collection")), Import.Selector.Unimport(Name.Indeterminate("BigInt")), Import.Selector.Wildcard()))))
    )
  }
}
