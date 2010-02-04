package org.jetbrains.plugins.scala.lang.resolve2



/**
 * Pavel.Fatin, 02.02.2010
 */

class ParameterTest extends ResolveTestBase {
  override def getTestDataPath: String = {
    super.getTestDataPath + "parameter/"
  }

  def testConstructorParameters = doTest
  def testFunctionParameters = doTest
  def testFunctionParameterClauses = doTest
  def testTypeParameters = doTest
}