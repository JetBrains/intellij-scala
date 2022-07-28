package org.jetbrains.plugins.scala.lang.resolve2

class ImportAliasClashTest extends ResolveTestBase {
  override def folderPath: String = {
    super.folderPath + "import/alias/clash/"
  }

  //TODO importexclude
//  def testRenameType1 = doTest
  //TODO importexclude
//  def testRenameType2 = doTest
  //TODO importexclude
//  def testRenameValue1 = doTest
  //TODO importexclude
//  def testRenameValue2 = doTest
  def testRenameTypeAndValue1(): Unit = doTest()
  def testRenameTypeAndValue2(): Unit = doTest()
  //TODO importexclude
//  def testRenameMultiple = doTest
  def testRepeatOther(): Unit = doTest()
  def testRepeatSame(): Unit = doTest()
}