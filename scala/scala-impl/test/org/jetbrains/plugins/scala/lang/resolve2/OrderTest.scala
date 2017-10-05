package org.jetbrains.plugins.scala.lang.resolve2


/**
 * Pavel.Fatin, 02.02.2010
 */

class OrderTest extends ResolveTestBase {
  override def folderPath: String = {
    super.folderPath + "order/"
  }

  def testBlock() = doTest()
  def testClass() = doTest()
  def testFile() = doTest()
  def testObject() = doTest()
  def testTrait() = doTest()
  def testOuterBlock() = doTest()
  def testOuterBlockNested() = doTest()
}