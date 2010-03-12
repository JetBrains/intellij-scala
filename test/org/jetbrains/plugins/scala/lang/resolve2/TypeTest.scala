package org.jetbrains.plugins.scala.lang.resolve2



/**
 * Pavel.Fatin, 02.02.2010
 */

class TypeTest extends ResolveTestBase {
  override def getTestDataPath: String = {
    super.getTestDataPath + "type/"
  }

  def testClassParameter = doTest
  def testClassTypeParameter = doTest
  def testFunction = doTest
  def testFunctionParameter = doTest
  def testFunctionTypeParameter = doTest
  def testValue = doTest
  def testVariable = doTest
}