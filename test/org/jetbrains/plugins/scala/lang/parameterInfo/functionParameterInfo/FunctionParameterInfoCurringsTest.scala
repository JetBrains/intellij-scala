package org.jetbrains.plugins.scala.lang.parameterInfo.functionParameterInfo

class FunctionParameterInfoCurringsTest extends FunctionParameterInfoTestBase {
  override def getTestDataPath: String =
    s"${super.getTestDataPath}currings/"

  def testApplyCurrings() = doTest()

  def testCurringDef() = doTest()

  def testFoldLeft() = doTest()

  def testFunctionTypeCurrings() = doTest()

  def testNoCurrings() = doTest()
}