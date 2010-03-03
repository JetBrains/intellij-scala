package org.jetbrains.plugins.scala.lang.resolve2


/**
 * Pavel.Fatin, 02.02.2010
 */

class InheritanceOverrideTest extends ResolveTestBase {
  override def getTestDataPath: String = {
    super.getTestDataPath + "inheritance/override/"
  }

  //TODO
//  def testCaseClass = doTest
  def testClass = doTest

  def testClassParameter = doTest
  def testClassParameterValue = doTest
  //TODO
//  def testClassParameterValueFrom = doTest
  def testClassParameterValueTo = doTest
  def testClassParameterVariable = doTest
  //TODO
//  def testClassParameterVariableFrom = doTest
  def testClassParameterVariableTo = doTest

  def testFunction = doTest
  //TODO
//  def testObject = doTest
  def testTrait = doTest
  def testValue = doTest
  def testVariable = doTest
}