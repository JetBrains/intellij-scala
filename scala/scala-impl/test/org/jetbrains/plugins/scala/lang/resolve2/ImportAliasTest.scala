package org.jetbrains.plugins.scala.lang.resolve2

class ImportAliasTest extends ResolveTestBase {
  override def folderPath: String = {
    super.folderPath + "import/alias/"
  }

  //TODO importexclude
//  def testExclude = doTest
  //TODO importexclude
//  def testExcludeOnRename = doTest
  def testHide(): Unit = doTest()
  def testRename(): Unit = doTest()
}