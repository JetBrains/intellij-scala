package org.jetbrains.plugins.scala.lang.resolve2


/**
 * Pavel.Fatin, 02.02.2010
 */

class FunctionAliasTest extends ResolveTestBase {
  override def getTestDataPath: String = {
    super.getTestDataPath + "function/alias/"
  }

  def testApply = doTest
}