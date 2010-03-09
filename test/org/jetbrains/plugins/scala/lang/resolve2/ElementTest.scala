package org.jetbrains.plugins.scala.lang.resolve2



/**
 * Pavel.Fatin, 02.02.2010
 */

class ElementTest extends ResolveTestBase {
  override def getTestDataPath: String = {
    super.getTestDataPath + "element/"
  }

  def testCaseClass = doTest
  def testCaseObject = doTest
  def testClass = doTest
  def testClassParameter = doTest
  def testClassParameterValue = doTest
  def testClassParameterVariable = doTest
  def testObject = doTest
  def testTrait = doTest
  def testFunctionDefinition = doTest
  def testFunctionParameter = doTest
  def testFunctionParameterClause = doTest
  def testConstructorParameter = doTest
  def testTypeAlias = doTest
  def testValue = doTest
  def testVariable = doTest
  //TODO answer? classof, it's internal compiler error, should be resolved, but another error in annotator
//  def testTypeParameterClass = doTest
  //TODO answer? classof, it's internal compiler error, should be resolved, but another error in annotator
//  def testTypeParameterFunction = doTest
  //TODO answer? classof, it's internal compiler error, should be resolved, but another error in annotator
//  def testTypeParameterTrait = doTest
  def testBinding = doTest
  def testValues = doTest
  def testStatementForValue = doTest
  def testStatementForValues = doTest
  def testStatementForBinding = doTest
  def testStatementForAssignment = doTest
  def testFunctionExpressionParameter = doTest
  def testCaseClauseParameter = doTest
  def testCaseClauseNamed = doTest
  def testCaseClauseBinding = doTest

  //TODO answer? classof, it's internal compiler error, should be resolved, but another error in annotator
//  def testPackage = doTest
  //TODO answer? classof, it's internal compiler error, should be resolved, but another error in annotator
//  def testPackageObject = doTest
}