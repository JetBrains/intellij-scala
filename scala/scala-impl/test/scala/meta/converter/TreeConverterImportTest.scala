package scala.meta.converter

import scala.meta.{TreeConverterTestBaseWithLibrary, _}


class TreeConverterImportTest extends TreeConverterTestBaseWithLibrary {

  def testImportSimple(): Unit = {
    doTest(
      "import scala.Any",
      Import(List(Importer(Term.Name("scala"), List(Importee.Name(Name.Indeterminate("Any"))))))
    )
  }
  
  def testImportMultipleClauses(): Unit = {
    doTest(
      "import scala.{Any, AnyRef}",
      Import(List(Importer(Term.Name("scala"), List(Importee.Name(Name.Indeterminate("Any")), Importee.Name(Name.Indeterminate("AnyRef"))))))
    )
  }

  def testImportWildCard(): Unit = {
    doTest(
      "import scala._",
      Import(List(Importer(Term.Name("scala"), List(Importee.Wildcard()))))
    )
  }
  
  def testImportRename(): Unit = {
    doTest(
      "import scala.{Any => asdf}",
      Import(List(Importer(Term.Name("scala"), List(Importee.Rename(Name.Indeterminate("Any"), Name.Indeterminate("asdf"))))))
    )
  }
  
  def testImportSelector(): Unit = {
    doTest(
      "import scala.collection.immutable",
      Import(List(Importer(Term.Select(Term.Name("scala"), Term.Name("collection")), List(Importee.Name(Name.Indeterminate("immutable"))))))
    )
  }

  def testImportMultiple(): Unit = {
    doTest(
      "import scala.collection, collection.immutable",
      Import(List(Importer(Term.Name("scala"), List(Importee.Name(Name.Indeterminate("collection")))), Importer(Term.Name("collection"), List(Importee.Name(Name.Indeterminate("immutable"))))))
    )
  }
  
  def testUnImport(): Unit = {
    doTest(
      "import scala.{collection => _}",
       Import(List(Importer(Term.Name("scala"), List(Importee.Unimport(Name.Indeterminate("collection"))))))
    )
  }

  def testImportAllExcept(): Unit = {
    doTest(
      "import scala.{collection => _, _}",
      Import(List(Importer(Term.Name("scala"), List(Importee.Unimport(Name.Indeterminate("collection")), Importee.Wildcard()))))
    )
  }

  def testMixedUnImport(): Unit = {
    doTest(
      "import scala.{collection, BigInt => _, _}",
      Import(List(Importer(Term.Name("scala"), List(Importee.Name(Name.Indeterminate("collection")), Importee.Unimport(Name.Indeterminate("BigInt")), Importee.Wildcard()))))
    )
  }
}
