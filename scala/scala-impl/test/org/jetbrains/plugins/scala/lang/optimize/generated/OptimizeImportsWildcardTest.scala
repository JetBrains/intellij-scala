package org.jetbrains.plugins.scala.lang.optimize.generated

import org.jetbrains.plugins.scala.lang.optimize.OptimizeImportsTestBase

/**
  * @author Nikolay.Tropin
  */
class OptimizeImportsWildcardTest extends OptimizeImportsTestBase {

  override def folderPath: String = super.folderPath + "wildcard/"

  def testMayReplace() = doTest()

  def testNotUsedNameClash() = doTest()

  def testUsedNameClash() = doTest()

  def testNameClash() = doTest()

  def testImplicitClass() = doTest()

  def testImplicitDef() = doTest()

  def testNameConflictTypeAlias() = doTest()

  def testShadowAndSelectors() = doTest()
}
