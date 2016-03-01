package org.jetbrains.plugins.scala.editor.importOptimizer

import junit.framework.TestCase
import org.jetbrains.plugins.scala.editor.importOptimizer.ScalaImportOptimizer._
import org.junit.Assert

class ImportTextCreatorTest extends TestCase {

  val textCreator = new ImportTextCreator()

  def testGetImportText_Root_And_Wildcard(): Unit = {
    val info = ImportInfo("scala.collection", None, Set.empty, Set.empty, Map.empty, Set.empty, hasWildcard = true, rootUsed = true)
    Assert.assertEquals("import _root_.scala.collection._", textCreator.getImportText(info, isUnicodeArrow = false, spacesInImports = false, sortLexicografically = true))
  }

  def testGetImportText_Hidden(): Unit = {
    val info = ImportInfo("scala", None, Set.empty, Set.empty, Map.empty, Set("Long"), hasWildcard = false, rootUsed = false)
    Assert.assertEquals("import scala.{Long => _}", textCreator.getImportText(info, isUnicodeArrow = false, spacesInImports = false, sortLexicografically = true))
  }

  def testGetImportText_Renames(): Unit = {
    val info = ImportInfo("java.lang", None, Set.empty, Set.empty, Map("Long" -> "JLong"), Set.empty, hasWildcard = false, rootUsed = false)
    Assert.assertEquals("import java.lang.{Long => JLong}", textCreator.getImportText(info, isUnicodeArrow = false, spacesInImports = false, sortLexicografically = true))
  }

  def testGetImportText_UnicodeArrowAndSpaces(): Unit = {
    val info = ImportInfo("java.lang", None, Set.empty, Set.empty, Map("Long" -> "JLong"), Set.empty, hasWildcard = false, rootUsed = false)
    Assert.assertEquals("import java.lang.{ Long ⇒ JLong }", textCreator.getImportText(info, isUnicodeArrow = true, spacesInImports = true, sortLexicografically = true))
  }

  def testGetImportText_SortSingles(): Unit = {
    val info = ImportInfo("java.lang", None, Set.empty,
      Set("Long", "Integer", "Float", "Short"), Map.empty, Set.empty, hasWildcard = false, rootUsed = false)
    Assert.assertEquals("import java.lang.{Float, Integer, Long, Short}", textCreator.getImportText(info, isUnicodeArrow = false, spacesInImports = false, sortLexicografically = true))
  }

  def testGetImportText_Renames_Hidden_Singles_Wildcard_Spaces(): Unit = {
    val info = ImportInfo("java.lang", None, Set.empty, Set("Integer", "Character", "Runtime"),
      Map("Long" -> "JLong", "Float" -> "JFloat"), Set("System"), hasWildcard = true, rootUsed = false)
    Assert.assertEquals("import java.lang.{ Character, Integer, Runtime, Float => JFloat, Long => JLong, System => _, _ }",
      textCreator.getImportText(info, isUnicodeArrow = false, spacesInImports = true, sortLexicografically = true))
  }

  def testGetImportText_No_Sorting(): Unit = {
    val info = ImportInfo("java.lang", None, Set.empty,
      Set("Long", "Integer", "Float", "Short"), Map.empty, Set.empty, hasWildcard = false, rootUsed = false)
    Assert.assertEquals("import java.lang.{Long, Integer, Float, Short}", textCreator.getImportText(info, isUnicodeArrow = false, spacesInImports = false, sortLexicografically = false))
  }
}