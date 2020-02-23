package org.jetbrains.plugins.scala.lang.resolve2


/**
 * Pavel.Fatin, 02.02.2010
 */

class InheritanceThisLegacyTest extends ResolveTestBase {
  override def folderPath: String = {
    super.folderPath + "inheritance/this/legacy/"
  }

  def testClass(): Unit = doTest()
  def testObject(): Unit = doTest()
  def testTrait(): Unit = doTest()
  def testClashClass(): Unit = doTest()
  def testClashObject(): Unit = doTest()
  def testClashTrait(): Unit = doTest()
}