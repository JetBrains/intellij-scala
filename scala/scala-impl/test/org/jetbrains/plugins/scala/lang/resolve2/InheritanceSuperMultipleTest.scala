package org.jetbrains.plugins.scala.lang.resolve2


/**
 * Pavel.Fatin, 02.02.2010
 */

class InheritanceSuperMultipleTest extends ResolveTestBase {
  override def folderPath: String = {
    super.folderPath + "inheritance/super/multiple/"
  }

  def testClashClass(): Unit = doTest()
  def testClashObject(): Unit = doTest()
  def testClashTrait(): Unit = doTest()
  def testClass(): Unit = doTest()
  def testObject(): Unit = doTest()
  def testTrait(): Unit = doTest()
}