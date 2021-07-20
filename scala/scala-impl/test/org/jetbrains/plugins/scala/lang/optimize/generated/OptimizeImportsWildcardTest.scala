package org.jetbrains.plugins.scala.lang.optimize.generated

import org.jetbrains.plugins.scala.lang.optimize.OptimizeImportsTestBase

/**
  * @author Nikolay.Tropin
  */
class OptimizeImportsWildcardTest extends OptimizeImportsTestBase {

  override def folderPath: String = super.folderPath + "wildcard/"

  def testMayReplace(): Unit = doTest()

  def testNotUsedNameClash(): Unit = doTest()

  def testUsedNameClash(): Unit = doTest()

  def testNameClash(): Unit = doTest()

  def testImplicitClass(): Unit = doTest()

  def testImplicitDef(): Unit = doTest()

  def testNameConflictTypeAlias(): Unit = doTest()

  def testShadowAndSelectors(): Unit = doTest()

  def testMergeIntoWildcard(): Unit = doTest()
}
