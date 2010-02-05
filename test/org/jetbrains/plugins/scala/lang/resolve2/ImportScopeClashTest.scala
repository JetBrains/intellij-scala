package org.jetbrains.plugins.scala.lang.resolve2


/**
 * Pavel.Fatin, 02.02.2010
 */

class ImportScopeClashTest extends ResolveTestBase {
  override def getTestDataPath: String = {
    super.getTestDataPath + "import/scope/clash/"
  }

  def testInnerBlock = doTest
  def testOuterBlock = doTest
  def testOuterBlockNested = doTest
  def testTwoBlocks = doTest
}