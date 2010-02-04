package org.jetbrains.plugins.scala.lang.resolve2



/**
 * Pavel.Fatin, 02.02.2010
 */

class ElementTest extends ResolveTestBase {
  override def getTestDataPath: String = {
    super.getTestDataPath + "element/"
  }

  //TODO
//  def testCaseClass = doTest
  def testClass = doTest
  def testObject = doTest
  def testTrait = doTest
  def testFunctionDefinition = doTest
  def testFunctionParameter = doTest
  def testFunctionParameterClause = doTest
  def testConstructorParameter = doTest
  def testTypeAlias = doTest
  def testValue = doTest
  def testVariable = doTest
  def testTypeParameter = doTest
  def testBinding = doTest
  def testValues = doTest
}