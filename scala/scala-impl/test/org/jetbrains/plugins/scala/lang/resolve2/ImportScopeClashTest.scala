package org.jetbrains.plugins.scala.lang.resolve2

class ImportScopeClashTest extends ResolveTestBase {
  override def folderPath: String = {
    super.folderPath + "import/scope/clash/"
  }

  def testInnerBlock(): Unit = doTest()
  def testOuterBlock(): Unit = doTest()
  def testOuterBlockNested(): Unit = doTest()
  def testTwoBlocks(): Unit = doTest()
}