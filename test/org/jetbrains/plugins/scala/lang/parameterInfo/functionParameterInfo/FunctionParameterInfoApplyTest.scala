package org.jetbrains.plugins.scala.lang.parameterInfo.functionParameterInfo

class FunctionParameterInfoApplyTest extends FunctionParameterInfoTestBase {

  override def getTestDataPath: String =
    s"${super.getTestDataPath}apply/"

  def testClassApply(): Unit = doTest()

  def testClassGenericApply(): Unit = doTest()

  def testGenericClassApply(): Unit = doTest()

  def testGenericClassApply2(): Unit = doTest()

  def testObjectApply(): Unit = doTest()

  def testObjectGenericApply(): Unit = doTest()

  def testAliasedApply(): Unit = doTest()
}