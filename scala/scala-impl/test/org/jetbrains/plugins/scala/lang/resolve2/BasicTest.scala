package org.jetbrains.plugins.scala.lang.resolve2



/**
 * Pavel.Fatin, 02.02.2010
 */

class BasicTest extends ResolveTestBase {
  override def folderPath: String = {
    super.folderPath + "basic/"
  }

  def testSimple(): Unit = doTest()
  def testMultipleDeclaration(): Unit = doTest()
  def testName(): Unit = doTest()
  def testToPattern(): Unit = doTest()
  def testGetClass(): Unit = doTest()
  def testNothing(): Unit = doTest()
}