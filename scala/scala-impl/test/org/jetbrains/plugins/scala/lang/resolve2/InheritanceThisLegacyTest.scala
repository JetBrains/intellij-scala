package org.jetbrains.plugins.scala.lang.resolve2


/**
 * Pavel.Fatin, 02.02.2010
 */

class InheritanceThisLegacyTest extends ResolveTestBase {
  override def folderPath: String = {
    super.folderPath + "inheritance/this/legacy/"
  }

  def testClass() = doTest()
  def testObject() = doTest()
  def testTrait() = doTest()
  def testClashClass() = doTest()
  def testClashObject() = doTest()
  def testClashTrait() = doTest()
}