package org.jetbrains.plugins.scalaDirective.lang.completion

import com.intellij.testFramework.TestIndexingModeSupporter.IndexingMode
import org.jetbrains.plugins.scala.lang.completion3.base.ScalaCompletionTestBase
import org.jetbrains.plugins.scala.util.runners.WithIndexingMode
import org.junit.Test

@WithIndexingMode(mode = IndexingMode.DUMB_EMPTY_INDEX)
final class ScalaDirectiveKeyCompletionTest extends ScalaCompletionTestBase {

  private def checkCompletion(key: String): Unit = {
    doCompletionTest(
      fileText = s"//> using $CARET",
      resultText = s"//> using $key$CARET",
      item = key
    )

    doCompletionTest(
      fileText = s"//> using ${key.head}$CARET",
      resultText = s"//> using $key$CARET",
      item = key
    )
  }

  /// DEPENDENCY KEYS

  @Test
  def testDep(): Unit = checkCompletion("dep")

  @Test
  def testDeps(): Unit = checkCompletion("deps")

  @Test
  def testDependencies(): Unit = checkCompletion("dependencies")

  @Test
  def testTestDep(): Unit = checkCompletion("test.dep")

  @Test
  def testTestDeps(): Unit = checkCompletion("test.deps")

  @Test
  def testTestDependencies(): Unit = checkCompletion("test.dependencies")

  @Test
  def testCompileOnlyDep(): Unit = checkCompletion("compileOnly.dep")

  @Test
  def testCompileOnlyDeps(): Unit = checkCompletion("compileOnly.deps")

  @Test
  def testCompileOnlyDependencies(): Unit = checkCompletion("compileOnly.dependencies")

  /// SCALA

  @Test
  def testScala(): Unit = checkCompletion("scala")
}
