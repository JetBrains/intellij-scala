package org.jetbrains.plugins.scala.lang.resolve2


/**
 * Pavel.Fatin, 02.02.2010
 */

class ImportScopeTest extends ResolveTestBase {
  override def folderPath: String = {
    super.folderPath + "import/scope/"
  }

  def testBlock() = doTest()
  def testInnerBlock() = doTest()
  def testOuterBlockNested() = doTest()
  def testOuterBlock() = doTest()
  def testTwoBlocks() = doTest()
}