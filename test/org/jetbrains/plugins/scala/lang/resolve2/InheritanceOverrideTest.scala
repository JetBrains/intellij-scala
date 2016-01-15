package org.jetbrains.plugins.scala.lang.resolve2


/**
 * Pavel.Fatin, 02.02.2010
 */

class InheritanceOverrideTest extends ResolveTestBase {
  override def folderPath: String = {
    super.folderPath + "inheritance/override/"
  }

  //TODO answer?
//  def testCaseClass = doTest
  def testClass() = doTest()
  def testClassParameter() = doTest()
  def testClassParameterValue() = doTest()
  //TODO classparameter
//  def testClassParameterValueFrom = doTest
  def testClassParameterValueTo() = doTest()
  def testClassParameterVariable() = doTest()
  //TODO classparameter
//  def testClassParameterVariableFrom = doTest
  def testClassParameterVariableTo() = doTest()
  def testFunction() = doTest()
  //TODO answer?
//  def testObject = doTest
  def testTrait() = doTest()
  def testValue() = doTest()
  def testVariable() = doTest()
}