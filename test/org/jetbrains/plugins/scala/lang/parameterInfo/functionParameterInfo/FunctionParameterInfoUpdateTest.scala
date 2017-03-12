package org.jetbrains.plugins.scala.lang.parameterInfo.functionParameterInfo

class FunctionParameterInfoUpdateTest extends FunctionParameterInfoTestBase {

  override def getTestDataPath: String =
    s"${super.getTestDataPath}update/"

  def testGenericUpdate() = doTest()

  def testNoUpdate() = doTest()

  def testUpdateOnly() = doTest()
}