package org.jetbrains.plugins.scala.refactoring.inline.generated

import org.jetbrains.plugins.scala.refactoring.inline.InlineRefactoringTestBase

/**
 * Created by user on 7/16/15.
 */
class InlineRefactoringTypeAliasTest extends InlineRefactoringTestBase {
  override def folderPath: String = super.folderPath + "typeAlias/"

  def testInlineSimple = doTest

  def testInlineProperty = doTest

  def testMultiple = doTest
}
