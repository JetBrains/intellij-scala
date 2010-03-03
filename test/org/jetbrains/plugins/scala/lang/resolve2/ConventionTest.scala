package org.jetbrains.plugins.scala.lang.resolve2


/**
 * Pavel.Fatin, 02.02.2010
 */

class ConventionTest extends ResolveTestBase {
  override def getTestDataPath: String = {
    super.getTestDataPath + "convention/"
  }

  def testApply = doTest
  def testRight = doTest
  def testRightIllegal = doTest
  def testUnary = doTest
  //TODO
  def testUnaryIllegal = doTest
}