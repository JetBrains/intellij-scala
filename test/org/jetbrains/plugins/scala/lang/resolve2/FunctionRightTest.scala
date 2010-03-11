package org.jetbrains.plugins.scala.lang.resolve2


/**
 * Pavel.Fatin, 02.02.2010
 */

class FunctionRightTest extends ResolveTestBase {
  override def getTestDataPath: String = {
    super.getTestDataPath + "function/right/"
  }

  def testRight = doTest
  def testRightIllegal = doTest
}