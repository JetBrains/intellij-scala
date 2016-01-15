package org.jetbrains.plugins.scala.refactoring.inline.generated

import org.jetbrains.plugins.scala.refactoring.inline.InlineRefactoringTestBase

class InlineRefactoringTypeAliasTest extends InlineRefactoringTestBase {
  override def folderPath: String = super.folderPath + "typeAlias/"

  def testInlineSimple() = doTest()

  def testMultiple() = doTest()

  def testStablePath() = doTest()
}
