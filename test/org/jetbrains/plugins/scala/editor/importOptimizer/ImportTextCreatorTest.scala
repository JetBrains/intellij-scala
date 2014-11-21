package org.jetbrains.plugins.scala.editor.importOptimizer

import junit.framework.TestCase
import org.jetbrains.plugins.scala.editor.importOptimizer.ScalaImportOptimizer._
import org.junit.Assert

class ImportTextCreatorTest extends TestCase {

  val textCreator = new ImportTextCreator()

  def testGetImportText_Root_And_Wildcard(): Unit = {
    val info = ImportInfo(Set.empty, "scala.collection", None, Set.empty, Set.empty, Map.empty, Set.empty, true, true)
    Assert.assertEquals("import _root_.scala.collection._", textCreator.getImportText(info, false, false, true))
  }

  def testGetImportText_Hidden(): Unit = {
    val info = ImportInfo(Set.empty, "scala", None, Set.empty, Set.empty, Map.empty, Set("Long"), false, false)
    Assert.assertEquals("import scala.{Long => _}", textCreator.getImportText(info, false, false, true))
  }

  def testGetImportText_Renames(): Unit = {
    val info = ImportInfo(Set.empty, "java.lang", None, Set.empty, Set.empty, Map("Long" -> "JLong"), Set.empty, false, false)
    Assert.assertEquals("import java.lang.{Long => JLong}", textCreator.getImportText(info, false, false, true))
  }

  def testGetImportText_UnicodeArrowAndSpaces(): Unit = {
    val info = ImportInfo(Set.empty, "java.lang", None, Set.empty, Set.empty, Map("Long" -> "JLong"), Set.empty, false, false)
    Assert.assertEquals("import java.lang.{ Long ⇒ JLong }", textCreator.getImportText(info, true, true, true))
  }

  def testGetImportText_SortSingles(): Unit = {
    val info = ImportInfo(Set.empty, "java.lang", None, Set.empty,
      Set("Long", "Integer", "Float", "Short"), Map.empty, Set.empty, false, false)
    Assert.assertEquals("import java.lang.{Float, Integer, Long, Short}", textCreator.getImportText(info, false, false, true))
  }

  def testGetImportText_Renames_Hidden_Singles_Wildcard_Spaces(): Unit = {
    val info = ImportInfo(Set.empty, "java.lang", None, Set.empty, Set("Integer", "Character", "Runtime"),
      Map("Long" -> "JLong", "Float" -> "JFloat"), Set("System"), true, false)
    Assert.assertEquals("import java.lang.{ Character, Float => JFloat, Integer, Long => JLong, Runtime, System => _, _ }",
      textCreator.getImportText(info, false, true, true))
  }

  def testGetImportText_No_Sorting(): Unit = {
    val info = ImportInfo(Set.empty, "java.lang", None, Set.empty,
      Set("Long", "Integer", "Float", "Short"), Map.empty, Set.empty, false, false)
    Assert.assertEquals("import java.lang.{Long, Integer, Float, Short}", textCreator.getImportText(info, false, false, false))
  }
}