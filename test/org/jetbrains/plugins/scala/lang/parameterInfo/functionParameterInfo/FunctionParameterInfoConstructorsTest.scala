package org.jetbrains.plugins.scala.lang.parameterInfo.functionParameterInfo

class FunctionParameterInfoConstructorsTest extends FunctionParameterInfoTestBase {

  override def getTestDataPath: String =
    s"${super.getTestDataPath}constructors/"

  def testAnnotations() = doTest()

  def testGenericScalaConstructor() = doTest()

  def testJavaConstructor() = doTest()

  def testNamingCaseClass() = doTest()

  def testScalaConstructor() = doTest()

  def testScalaConstructorA() = doTest()

  def testScalaConstructorB() = doTest()
  
  def testScalaConstructorC() = doTest()

  def testScalaConstructorD() = doTest()

  def testSelfInvocation() = doTest()

  def testThisScalaConstructor() = doTest()

  def testAliasedConstructor() = doTest()
}