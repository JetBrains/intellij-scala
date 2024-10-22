package org.jetbrains.plugins.scalaDirective.lang.completion

import com.intellij.testFramework.TestIndexingModeSupporter.IndexingMode
import org.jetbrains.plugins.scala.lang.completion3.base.ScalaCompletionTestBase
import org.jetbrains.plugins.scala.util.runners.WithIndexingMode

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

  def testDep(): Unit = checkCompletion("dep")

  def testDeps(): Unit = checkCompletion("deps")

  def testDependencies(): Unit = checkCompletion("dependencies")

  def testTestDep(): Unit = checkCompletion("test.dep")

  def testTestDeps(): Unit = checkCompletion("test.deps")

  def testTestDependencies(): Unit = checkCompletion("test.dependencies")

  def testCompileOnlyDep(): Unit = checkCompletion("compileOnly.dep")

  def testCompileOnlyDeps(): Unit = checkCompletion("compileOnly.deps")

  def testCompileOnlyDependencies(): Unit = checkCompletion("compileOnly.dependencies")

  /// SCALA

  def testScala(): Unit = checkCompletion("scala")
}
