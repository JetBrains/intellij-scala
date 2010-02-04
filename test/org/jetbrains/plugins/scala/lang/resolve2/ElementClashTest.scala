package org.jetbrains.plugins.scala.lang.resolve2



/**
 * Pavel.Fatin, 02.02.2010
 */

class ElementClashTest extends ResolveTestBase {
  override def getTestDataPath: String = {
    super.getTestDataPath + "element/clash/"
  }

  //TODO
//  def testClass = doTest
  //TODO
//  def testCaseClass = doTest
  //TODO
//  def testTrait = doTest
  def testObject = doTest
  def testFunctionDefinition = doTest
  def testFunctionParameter = doTest
  def testFunctionParameterClause = doTest
  def testConstructorParameter = doTest
  //TODO
//  def testTypeAlias = doTest
  def testValue = doTest
  def testVariable = doTest
  //TODO
//  def testTypeParameter = doTest
}