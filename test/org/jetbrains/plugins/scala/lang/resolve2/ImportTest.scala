package org.jetbrains.plugins.scala.lang.resolve2


/**
 * Pavel.Fatin, 02.02.2010
 */

class ImportTest extends ResolveTestBase {
  override def getTestDataPath: String = {
    super.getTestDataPath + "import/"
  }

  def testAll = doTest
  def testBrokenChain = doTest
  def testLocal1 = doTest
  def testLocal2 = doTest
  def testMultiple = doTest
  def testSelection = doTest
  def testSingle = doTest
}