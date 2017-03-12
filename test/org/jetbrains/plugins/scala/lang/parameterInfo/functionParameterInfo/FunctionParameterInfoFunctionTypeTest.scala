package org.jetbrains.plugins.scala.lang.parameterInfo.functionParameterInfo

class FunctionParameterInfoFunctionTypeTest extends FunctionParameterInfoTestBase {
  override def getTestDataPath: String =
    s"${super.getTestDataPath}functionType/"

  def testFunctionType() = doTest()

  def testFunctionTypeTwo() = doTest()

  def testNamingFunctionType() = doTest()
}