package org.jetbrains.plugins.scala.lang.resolve2


/**
 * Pavel.Fatin, 02.02.2010
 */

class BasicConventionTest extends ResolveTestBase {
  override def getTestDataPath: String = {
    super.getTestDataPath + "basic/convention/"
  }

  def testApply = doTest
  def testRight = doTest
  def testRightIllegal = doTest
  def testUnary = doTest
  //TODO
//  def testUnaryIllegal = doTest
}