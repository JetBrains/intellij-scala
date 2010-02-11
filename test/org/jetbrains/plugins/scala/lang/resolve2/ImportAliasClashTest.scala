package org.jetbrains.plugins.scala.lang.resolve2



/**
 * Pavel.Fatin, 02.02.2010
 */

class ImportAliasClashTest extends ResolveTestBase {
  override def getTestDataPath: String = {
    super.getTestDataPath + "import/alias/clash/"
  }

  //TODO
//  def testRenameType1 = doTest
  //TODO
//  def testRenameType2 = doTest
  //TODO
//  def testRenameValue1 = doTest
  //TODO
//  def testRenameValue2 = doTest
  //TODO
//  def testRenameTypeAndValue1 = doTest
  //TODO
//  def testRenameTypeAndValue2 = doTest
  //TODO
//  def testRenameMultiple = doTest
  //TODO
//  def testRepeatOther = doTest
  def testRepeatSame = doTest
}