package org.jetbrains.plugins.scala.lang.resolve2



/**
 * Pavel.Fatin, 02.02.2010
 */

class ImportAliasClashTest extends ResolveTestBase {
  override def getTestDataPath: String = {
    super.getTestDataPath + "import/alias/clash/"
  }

  //TODO importexclude
//  def testRenameType1 = doTest
  //TODO importexclude
//  def testRenameType2 = doTest
  //TODO importexclude
//  def testRenameValue1 = doTest
  //TODO importexclude
//  def testRenameValue2 = doTest
  def testRenameTypeAndValue1 = doTest
  def testRenameTypeAndValue2 = doTest
  //TODO importexclude
//  def testRenameMultiple = doTest
  def testRepeatOther = doTest
  def testRepeatSame = doTest
}