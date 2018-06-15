package org.jetbrains.plugins.scala.lang.parameterInfo.functionParameterInfo

class FunctionParameterInfoConstructorsTest extends FunctionParameterInfoTestBase {

  override def getTestDataPath: String =
    s"${super.getTestDataPath}constructors/"

  def testAnnotations(): Unit = doTest()

  def testMemberModifiers(): Unit = doTest()

  def testCaseClass(): Unit = doTest()

  def testGenericScalaConstructor(): Unit = doTest()

  def testJavaConstructor(): Unit = doTest()

  def testNamingCaseClass(): Unit = doTest()

  def testScalaConstructor(): Unit = doTest()

  def testScalaConstructorA(): Unit = doTest()

  def testScalaConstructorB(): Unit = doTest()
  
  def testScalaConstructorC(): Unit = doTest()

  def testScalaConstructorD(): Unit = doTest()

  def testSelfInvocation(): Unit = doTest()

  def testThisScalaConstructor(): Unit = doTest()

  def testAliasedConstructor(): Unit = doTest()
}