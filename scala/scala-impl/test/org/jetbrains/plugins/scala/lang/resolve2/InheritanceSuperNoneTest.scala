package org.jetbrains.plugins.scala.lang.resolve2


/**
 * Pavel.Fatin, 02.02.2010
 */

class InheritanceSuperNoneTest extends ResolveTestBase {
  override def folderPath: String = {
    super.folderPath + "inheritance/super/none/"
  }

  def testBlock() = doTest()
  def testClass() = doTest()
  def testFile() = doTest()
  def testFunction() = doTest()
  def testObject() = doTest()
  def testTrait() = doTest()
}