package org.jetbrains.plugins.scala.lang.resolve2


/**
 * Pavel.Fatin, 02.02.2010
 */

class ScopeTest extends ResolveTestBase {
  override def folderPath: String = {
    super.folderPath + "scope/"
  }

  def testBlock() = doTest()
  def testOuterBlock() = doTest()
  def testOuterBlockNested() = doTest()
  def testInnerBlock() = doTest()
  //TODO packageobject
//  def testPackageObject = doTest
  def testPackageObjectChild() = doTest()
  //TODO packageobject
//  def testPackageObjectParent = doTest
  def testTwoBlocks() = doTest()

  def testDefaultParameterInNextClause() {doTest()}
}