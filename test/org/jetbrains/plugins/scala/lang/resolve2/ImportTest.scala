package org.jetbrains.plugins.scala.lang.resolve2


/**
 * Pavel.Fatin, 02.02.2010
 */

class ImportTest extends ResolveTestBase {
  override def getTestDataPath: String = {
    super.getTestDataPath + "import/"
  }

  def testAll = doTest
  def testMultiple = doTest
  def testSelection = doTest
  def testSingle = doTest
  //TODO
//  def testLocal = doTest
  //TODO
//  def testLoca2 = doTest
}