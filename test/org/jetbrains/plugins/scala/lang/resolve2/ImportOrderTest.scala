package org.jetbrains.plugins.scala.lang.resolve2


/**
 * Pavel.Fatin, 02.02.2010
 */

class ImportOrderTest extends ResolveTestBase {
  override def getTestDataPath: String = {
    super.getTestDataPath + "import/order/"
  }

  def testBlock = doTest
  def testClass = doTest
  def testFile = doTest
  def testOuterBlockNested = doTest
  def testObject = doTest
  def testOuterBlock = doTest
  def testTrait = doTest
}