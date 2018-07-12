package org.jetbrains.plugins.scala.lang.parameterInfo.functionParameterInfo

class FunctionParameterInfoSimpleTest extends FunctionParameterInfoTestBase {

  override def getTestDataPath: String =
    s"${super.getTestDataPath}simple/"
  
  def testAnnotation() = doTest()

  def testDefaultParameter() = doTest()

  def testDefaultParameterFromSources() = doTest()

  def testFromPositionalToNaming() = doTest()

  def testGenericJavaLibrary() = doTest()

  def testImplicitParameter() = doTest()

  def testInfixExpression() = doTest()

  def testInfixTuple() = doTest()

  def testInfixUnit() = doTest()

  def testJavaLibrary() = doTest()

  def testLocal() = doTest()

  def testNothingExprType() = doTest()

  def testPositionalAfterNamed() = doTest()

  def testScalaLibrary() = doTest()

  def testSimple() = doTest()

  def testSyntheticParameter() = doTest()

  def testTypeRefinement() = doTest()

  def testAliasedMethod() = doTest()
}