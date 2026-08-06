package org.jetbrains.plugins.scala.lang.parameterInfo.functionParameterInfo

import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

class FunctionParameterInfoUniversalApplyTest extends FunctionParameterInfoTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= LatestScalaVersions.Scala_3_0

  override def getTestDataPath: String =
    s"${super.getTestDataPath}universalApply/"

  def testAliasedConstructor(): Unit = doTest()

  def testAnnotations(): Unit = doTest()

  def testContextBound(): Unit = doTest()

  def testGenericScalaConstructor(): Unit = doTest()

  def testJavaConstructor(): Unit = doTest()

  def testMemberModifiers(): Unit = doTest()

  def testNamingCaseClass(): Unit = doTest()

  def testScalaConstructor(): Unit = doTest()

  def testScalaConstructorA(): Unit = doTest()

  def testScalaConstructorB(): Unit = doTest()

  def testScalaConstructorC(): Unit = doTest()

  def testScalaConstructorD(): Unit = doTest()

  def testScalaConstructorE(): Unit = doTest()

  def testScalaConstructorF(): Unit = doTest()

  def testSelfInvocation(): Unit = doTest()

  def testThisScalaConstructor(): Unit = doTest()
}
