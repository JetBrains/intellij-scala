package org.jetbrains.plugins.scala.lang.resolve2



/**
 * Pavel.Fatin, 02.02.2010
 */

class BasicTest extends ResolveTestBase {
  override def folderPath: String = {
    super.folderPath + "basic/"
  }

  def testSimple() = doTest()
  def testMultipleDeclaration() = doTest()
  def testName() = doTest()
  def testToPattern() = doTest()
  def testGetClass() = doTest()
  def testNothing() = doTest()
}