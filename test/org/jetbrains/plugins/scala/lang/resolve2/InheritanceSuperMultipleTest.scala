package org.jetbrains.plugins.scala.lang.resolve2


/**
 * Pavel.Fatin, 02.02.2010
 */

class InheritanceSuperMultipleTest extends ResolveTestBase {
  override def getTestDataPath: String = {
    super.getTestDataPath + "inheritance/super/multiple/"
  }

  def testClashClass = doTest
  def testClashObject = doTest
  def testClashTrait = doTest
  def testClass = doTest
  def testObject = doTest
  def testTrait = doTest
}