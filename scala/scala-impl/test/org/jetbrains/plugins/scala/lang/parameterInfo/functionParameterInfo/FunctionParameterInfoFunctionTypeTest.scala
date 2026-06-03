package org.jetbrains.plugins.scala.lang.parameterInfo.functionParameterInfo

class FunctionParameterInfoFunctionTypeTest extends FunctionParameterInfoTestBase {
  override def getTestDataPath: String =
    s"${super.getTestDataPath}functionType/"

  def testCustomInterpolatorFunctionType(): Unit = doTest()

  def testFunctionType(): Unit = doTest()

  def testFunctionTypeTwo(): Unit = doTest()

  def testNamingFunctionType(): Unit = doTest()
}