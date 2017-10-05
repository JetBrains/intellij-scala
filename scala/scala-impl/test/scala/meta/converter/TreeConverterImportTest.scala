package scala.meta.converter

import scala.meta.TreeConverterTestBaseWithLibrary
import scala.meta._


class TreeConverterImportTest extends TreeConverterTestBaseWithLibrary {

  def testImportSimple() {
    doTest(
      "import scala.Any",
      Import(List(Importer(Term.Name("scala"), List(Importee.Name(Name.Indeterminate("Any"))))))
    )
  }
  
  def testImportMultipleClauses() {
    doTest(
      "import scala.{Any, AnyRef}",
      Import(List(Importer(Term.Name("scala"), List(Importee.Name(Name.Indeterminate("Any")), Importee.Name(Name.Indeterminate("AnyRef"))))))
    )
  }

  def testImportWildCard() {
    doTest(
      "import scala._",
      Import(List(Importer(Term.Name("scala"), List(Importee.Wildcard()))))
    )
  }
  
  def testImportRename() {
    doTest(
      "import scala.{Any => asdf}",
      Import(List(Importer(Term.Name("scala"), List(Importee.Rename(Name.Indeterminate("Any"), Name.Indeterminate("asdf"))))))
    )
  }
  
  def testImportSelector() {
    doTest(
      "import scala.collection.immutable",
      Import(List(Importer(Term.Select(Term.Name("scala"), Term.Name("collection")), List(Importee.Name(Name.Indeterminate("immutable"))))))
    )
  }

  def testImportMultiple() {
    doTest(
      "import scala.collection, collection.immutable",
      Import(List(Importer(Term.Name("scala"), List(Importee.Name(Name.Indeterminate("collection")))), Importer(Term.Name("collection"), List(Importee.Name(Name.Indeterminate("immutable"))))))
    )
  }
  
  def testUnImport() {
    doTest(
      "import scala.{collection => _}",
       Import(List(Importer(Term.Name("scala"), List(Importee.Unimport(Name.Indeterminate("collection"))))))
    )
  }

  def testImportAllExcept() {
    doTest(
      "import scala.{collection => _, _}",
      Import(List(Importer(Term.Name("scala"), List(Importee.Unimport(Name.Indeterminate("collection")), Importee.Wildcard()))))
    )
  }

  def testMixedUnImport() {
    doTest(
      "import scala.{collection, BigInt => _, _}",
      Import(List(Importer(Term.Name("scala"), List(Importee.Name(Name.Indeterminate("collection")), Importee.Unimport(Name.Indeterminate("BigInt")), Importee.Wildcard()))))
    )
  }
}
