package org.jetbrains.plugins.scala.lang.parameterInfo.functionParameterInfo
import org.jetbrains.plugins.scala.{ScalaVersion, Scala_2_11}

class FunctionParameterInfoCurringsTest extends FunctionParameterInfoTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version >= Scala_2_11

  override def getTestDataPath: String =
    s"${super.getTestDataPath}currings/"

  def testApplyCurrings(): Unit = doTest()

  def testCurringDef(): Unit = doTest()

  def testFoldLeft(): Unit = doTest()

  def testFunctionTypeCurrings(): Unit = doTest()

  def testNoCurrings(): Unit = doTest()
}