package org.jetbrains.plugins.scala.editor.importOptimizer

import junit.framework.TestCase
import org.jetbrains.plugins.scala.editor.importOptimizer.ScalaImportOptimizer.ImportTextCreator
import org.jetbrains.plugins.scala.project.ScalaFeatures
import org.junit.Assert.assertEquals

class ImportTextCreatorTest extends TestCase {
  private val lexOrdering = Some(Ordering.String)
  private val scalastyleOrdering = Some(ScalastyleImportsUtil.nameOrdering)
  private val textCreator = new ImportTextCreator()

  private def getImportText(info: ImportInfo,
                            isUnicodeArrow: Boolean = false,
                            spacesInImports: Boolean = false,
                            scala3Features: ScalaFeatures = ScalaFeatures.default,
                            nameOrdering: Option[Ordering[String]] = lexOrdering,
                            forceScala2SyntaxInSource3: Boolean = false): String =
    textCreator.getImportText(info, ImportTextGenerationOptions(isUnicodeArrow, spacesInImports, scala3Features, nameOrdering, forceScala2SyntaxInSource3))

  def testGetImportText_Root_And_Wildcard(): Unit = {
    val info = ImportInfo(Some("scala.collection"), hasWildcard = true, rootUsed = true)
    assertEquals("import _root_.scala.collection._", getImportText(info))
  }

  def testGetImportText_Hidden(): Unit = {
    val info = ImportInfo(Some("scala"), hiddenNames = Set("Long"))
    assertEquals("import scala.{Long => _}", getImportText(info))
  }

  def testGetImportText_Renames(): Unit = {
    val info = ImportInfo(Some("java.lang"), renames = Map("Long" -> "JLong"))
    assertEquals("import java.lang.{Long => JLong}", getImportText(info))
  }

  def testGetImportText_UnicodeArrowAndSpaces(): Unit = {
    val info = ImportInfo(Some("java.lang"), renames = Map("Long" -> "JLong"))
    assertEquals("import java.lang.{ Long ⇒ JLong }", getImportText(info, isUnicodeArrow = true, spacesInImports = true))
  }

  def testGetImportText_source3(): Unit = {
    val info = ImportInfo(Some("java.lang"), renames = Map("Long" -> "JLong"))
    assertEquals("import java.lang.Long as JLong", getImportText(info, scala3Features = ScalaFeatures.`-Xsource:3 in 2.12.14 or 2.13.6`))
    assertEquals("import java.lang.Long as JLong", getImportText(info, scala3Features = ScalaFeatures.`-Xsource:3 in 2.12.15 or 2.13.7`))
  }


  def testGetImportText_SortSingles(): Unit = {
    val info = ImportInfo(Some("java.lang"), singleNames = Set("Long", "Integer", "Float", "Short"))
    assertEquals("import java.lang.{Float, Integer, Long, Short}", getImportText(info))
  }

  def testGetImportText_Renames_Hidden_Singles_Wildcard_Spaces(): Unit = {
    val info = ImportInfo(Some("java.lang"),
      singleNames = Set("Integer", "Character", "Runtime"),
      renames = Map("Long" -> "JLong", "Float" -> "JFloat"),
      hiddenNames = Set("System"),
      hasWildcard = true)
    assertEquals("import java.lang.{ Character, Integer, Runtime, Float => JFloat, Long => JLong, System => _, _ }",
      getImportText(info, spacesInImports = true))
  }

  def testGetImportText_Renames_Hidden_Singles_Wildcard_Spaces_in_source3(): Unit = {
    val info = ImportInfo(Some("java.lang"),
      singleNames = Set("Integer", "Character", "Runtime"),
      renames = Map("Long" -> "JLong", "Float" -> "JFloat"),
      hiddenNames = Set("System"),
      hasWildcard = true)
    assertEquals("import java.lang.{ Character, Integer, Runtime, Float as JFloat, Long as JLong, System as _, _ }",
      getImportText(info, spacesInImports = true, scala3Features = ScalaFeatures.`-Xsource:3 in 2.12.14 or 2.13.6`))
    assertEquals("import java.lang.{ Character, Integer, Runtime, Float as JFloat, Long as JLong, System as _, * }",
      getImportText(info, spacesInImports = true, scala3Features = ScalaFeatures.`-Xsource:3 in 2.12.15 or 2.13.7`))
  }

  def testWildcardOnly_in_source3(): Unit = {
    val info = ImportInfo(Some("java.lang"), hasWildcard = true)
    assertEquals("import java.lang.*",
      getImportText(info, spacesInImports = true, scala3Features = ScalaFeatures.`-Xsource:3 in 2.12.14 or 2.13.6`))
    assertEquals("import java.lang.*",
      getImportText(info, spacesInImports = true, scala3Features = ScalaFeatures.`-Xsource:3 in 2.12.15 or 2.13.7`))
  }

  def testForceScala2InSource3(): Unit = {
    {
      val info = ImportInfo(Some("java.lang"),
        singleNames = Set("Integer", "Character", "Runtime"),
        renames = Map("Long" -> "JLong", "Float" -> "JFloat"),
        hiddenNames = Set("System"),
        hasWildcard = true)
      assertEquals("import java.lang.{ Character, Integer, Runtime, Float => JFloat, Long => JLong, System => _, _ }",
        getImportText(info, spacesInImports = true, scala3Features = ScalaFeatures.`-Xsource:3 in 2.12.14 or 2.13.6`, forceScala2SyntaxInSource3 = true))
      assertEquals("import java.lang.{ Character, Integer, Runtime, Float => JFloat, Long => JLong, System => _, _ }",
        getImportText(info, spacesInImports = true, scala3Features = ScalaFeatures.`-Xsource:3 in 2.12.15 or 2.13.7`, forceScala2SyntaxInSource3 = true))
    }

    {
      val info = ImportInfo(Some("java.lang"), hasWildcard = true)
      assertEquals("import java.lang._",
        getImportText(info, spacesInImports = true, scala3Features = ScalaFeatures.`-Xsource:3 in 2.12.14 or 2.13.6`, forceScala2SyntaxInSource3 = true))
      assertEquals("import java.lang._",
        getImportText(info, spacesInImports = true, scala3Features = ScalaFeatures.`-Xsource:3 in 2.12.15 or 2.13.7`, forceScala2SyntaxInSource3 = true))
    }
  }

  def testGetImportText_No_Sorting(): Unit = {
    val info = ImportInfo(Some("java.lang"), singleNames = Set("Long", "Integer", "Float", "Short"))
    assertEquals("import java.lang.{Long, Integer, Float, Short}", getImportText(info, nameOrdering = None))
  }

  def testLexSorting(): Unit = {
    val info = ImportInfo(Some("java.io"), singleNames = Set("InputStream", "IOException", "SequenceInputStream"))
    assertEquals("import java.io.{IOException, InputStream, SequenceInputStream}",
      getImportText(info))
  }

  def testScalastyleSorting(): Unit = {
    val info = ImportInfo(Some("java.io"), singleNames = Set("IOException", "InputStream", "SequenceInputStream"))
    assertEquals("import java.io.{InputStream, IOException, SequenceInputStream}",
      getImportText(info, nameOrdering = scalastyleOrdering))
  }

  def testScalastyleSortingPrefix(): Unit = {
    import textCreator.getScalastyleSortableText

    assertEquals("bar.baz.abc.foo", getScalastyleSortableText(ImportInfo(Some("bar.baz.abc"), singleNames = Set("foo"))))
    assertEquals("bar.baz.abc.",    getScalastyleSortableText(ImportInfo(Some("bar.baz.abc"), singleNames = Set("foo", "bar"))))
    assertEquals("bar.baz.abc.",    getScalastyleSortableText(ImportInfo(Some("bar.baz.abc"), renames = Map("x" -> "y"))))
    assertEquals("bar.baz.abc._",   getScalastyleSortableText(ImportInfo(Some("bar.baz.abc"), hasWildcard = true)))
  }

  def testUnqualifiedScala3RenamingImport(): Unit = {
    val info = ImportInfo(None, renames = Map("foo" -> "bar"))
    assertEquals("import foo as bar", getImportText(info, scala3Features = ScalaFeatures.`-Xsource:3 in 2.12.14 or 2.13.6`))
  }
}
