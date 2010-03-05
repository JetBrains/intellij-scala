package org.jetbrains.plugins.scala.lang.resolve2


/**
 * Pavel.Fatin, 02.02.2010
 */

class PredefClashTest extends ResolveTestBase {
  override def getTestDataPath: String = {
    super.getTestDataPath + "predef/clash/"
  }

  def testInherited = doTest
  def testLocal1 = doTest
  def testLocal2 = doTest
  def testOuterScope = doTest
}