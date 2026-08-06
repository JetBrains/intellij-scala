package org.jetbrains.plugins.scala.lang.parameterInfo.functionParameterInfo

class FunctionParameterInfoUpdateTest extends FunctionParameterInfoTestBase {

  override def getTestDataPath: String =
    s"${super.getTestDataPath}update/"

  def testGenericUpdate(): Unit = doTest()

  def testNoUpdate(): Unit = doTest()

  def testUpdateOnly(): Unit = doTest()
}