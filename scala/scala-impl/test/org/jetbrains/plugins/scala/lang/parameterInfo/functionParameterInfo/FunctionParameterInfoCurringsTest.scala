package org.jetbrains.plugins.scala.lang.parameterInfo.functionParameterInfo

import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

class FunctionParameterInfoCurringsTest extends FunctionParameterInfoTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version >= LatestScalaVersions.Scala_2_11

  override def getTestDataPath: String =
    s"${super.getTestDataPath}currings/"

  def testApplyCurrings(): Unit = doTest()

  def testCurringDef(): Unit = doTest()

  def testFoldLeft(): Unit = doTest()

  def testFunctionTypeCurrings(): Unit = doTest()

  def testNoCurrings(): Unit = doTest()

  def testTransitiveApplyCurrings(): Unit = doTest()

  def testTransitiveApplyCurrings2(): Unit = doTest()
}
