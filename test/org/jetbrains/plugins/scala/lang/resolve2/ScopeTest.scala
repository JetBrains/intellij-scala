package org.jetbrains.plugins.scala.lang.resolve2


/**
 * Pavel.Fatin, 02.02.2010
 */

class ScopeTest extends ResolveTestBase {
  override def getTestDataPath: String = {
    super.getTestDataPath + "scope/"
  }

  def testBlock = doTest
  def testOuterBlock = doTest
  def testOuterBlockNested = doTest
  def testInnerBlock = doTest
  //TODO
//  def testPackageObject = doTest
  def testPackageObjectChild = doTest
  //TODO
//  def testPackageObjectParent = doTest
  def testTwoBlocks = doTest
}