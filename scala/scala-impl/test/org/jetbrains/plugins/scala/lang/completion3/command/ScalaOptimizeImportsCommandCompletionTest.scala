package org.jetbrains.plugins.scala.lang.completion3.command

import com.intellij.codeInsight.lookup.LookupElement
import org.junit.Assert.assertNull
import org.junit.Test

final class ScalaOptimizeImportsCommandCompletionTest extends ScalaCommandCompletionTestBase {
  private val OptimizeImportsPredicate: LookupElement => Boolean = lookupStringStartsWith(_, "Optimize import")

  private def doOptimizeImportsCommandCompletionTest(fileText: String, resultText: String): Unit = {
    val lookup = doCommandCompletionTest(fileText, resultText = resultText, predicate = OptimizeImportsPredicate)
    assertNull("No highlighting is expected for Optimize Imports command completion", lookup.getHighlighting)
  }

  private def checkNoOptimizeImportsCommandCompletion(fileText: String): Unit =
    checkNoCommandCompletion(fileText, predicate = OptimizeImportsPredicate)

  @Test
  def optimizeImport(): Unit = doOptimizeImportsCommandCompletionTest(
    fileText =
      s"""import java.util.ArrayList.$CARET
         |
         |class Test {
         |}""".stripMargin,
    resultText =
      s"""
         |
         |class Test {
         |}""".stripMargin
  )

  @Test
  def optimizeImportOnKeyword(): Unit = doOptimizeImportsCommandCompletionTest(
    fileText =
      s"""import.$CARET java.util.ArrayList
         |
         |class Test {
         |}""".stripMargin,
    resultText =
      s"""
         |
         |class Test {
         |}""".stripMargin
  )

  @Test
  def optimizeImportOnNewLine(): Unit = doOptimizeImportsCommandCompletionTest(
    fileText =
      s"""import java.util.ArrayList
         |.$CARET
         |class Test {
         |}""".stripMargin,
    resultText =
      s"""
         |
         |class Test {
         |}""".stripMargin
  )

  @Test
  def optimizeImportOnLocalImport(): Unit = doOptimizeImportsCommandCompletionTest(
    fileText =
      s"""import java.util.ArrayList
         |import java.io.File
         |
         |class Test {
         |  import java.net.URI.$CARET
         |
         |  def test(file: File): Unit = {}
         |}""".stripMargin,
    resultText =
      s"""import java.io.File
         |
         |class Test {
         |
         |  def test(file: File): Unit = {}
         |}""".stripMargin
  )

  @Test
  def multipleUnusedImports(): Unit = doOptimizeImportsCommandCompletionTest(
    fileText =
      s"""import java.util.ArrayList
         |import java.util.HashMap.$CARET
         |
         |class Test {
         |}""".stripMargin,
    resultText =
      s"""
         |
         |class Test {
         |}""".stripMargin
  )

  @Test
  def keepUsedImport(): Unit = doOptimizeImportsCommandCompletionTest(
    fileText =
      s"""import java.util.ArrayList
         |import java.io.File.$CARET
         |
         |class Test {
         |  def test(f: File): Unit = {}
         |}""".stripMargin,
    resultText =
      s"""import java.io.File
         |
         |class Test {
         |  def test(f: File): Unit = {}
         |}""".stripMargin
  )

  @Test
  def groupedImportAllUnused(): Unit = doOptimizeImportsCommandCompletionTest(
    fileText =
      s"""import java.util.{ArrayList, HashMap}.$CARET
         |
         |class Test {
         |}""".stripMargin,
    resultText =
      s"""
         |
         |class Test {
         |}""".stripMargin
  )

  @Test
  def groupedImportPartiallyUsed(): Unit = doOptimizeImportsCommandCompletionTest(
    fileText =
      s"""import java.io.{File, InputStream}.$CARET
         |
         |class Test {
         |  def test(f: File): Unit = {}
         |}""".stripMargin,
    resultText =
      s"""import java.io.File
         |
         |class Test {
         |  def test(f: File): Unit = {}
         |}""".stripMargin
  )

  @Test
  def alreadyOptimizedImport(): Unit = doOptimizeImportsCommandCompletionTest(
    fileText =
      s"""import java.io.File.$CARET
         |
         |class Test {
         |  def test(f: File): Unit = {}
         |}""".stripMargin,
    resultText =
      s"""import java.io.File
         |
         |class Test {
         |  def test(f: File): Unit = {}
         |}""".stripMargin
  )

  @Test
  def noCompletionOnClass(): Unit = checkNoOptimizeImportsCommandCompletion(
    fileText =
      s"""import java.util.ArrayList
         |
         |object Test {
         |}..$CARET""".stripMargin
  )
}
