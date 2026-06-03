package org.jetbrains.plugins.scala.refactoring.inline.generated

import org.jetbrains.plugins.scala.extensions.PathExt
import org.jetbrains.plugins.scala.refactoring.inline.InlineRefactoringTestBase

import java.nio.file.Path

class InlineRefactoringTypeAliasTest extends InlineRefactoringTestBase {
  override def folderPath: Path = super.folderPath / "typeAlias"

  def testInlineSimple(): Unit = doTest()

  def testMultiple(): Unit = doTest()

  def testMultipleFromUsage(): Unit = doTest()

  def testStablePath(): Unit = doTest()

  def testInlineAndKeep(): Unit = doTest()

  def testInlineAndKeepMultiple(): Unit = doTest()

  def testInlineAndKeepMultipleFromUsage(): Unit = doTest()

  def testInlineOnlyCurrentUsage(): Unit = doTest()
}
