package org.jetbrains.plugins.scala.lang.parameterInfo.functionParameterInfo

import org.jetbrains.plugins.scala.ScalaVersion

class FunctionParameterInfoTypeParameterTest extends FunctionParameterInfoTestBase {
  override def getTestDataPath: String =
    s"${super.getTestDataPath}typeParameters/"

  def testContextBound(): Unit = doTest()

  def testContextBoundDesugared(): Unit = doTest()

  def testImplicitClause(): Unit = doTest()

  def testImplicitParameter(): Unit = doTest()

  def testLowerBound(): Unit = doTest()

  def testTypeParameters(): Unit = doTest()

  def testUpperBound(): Unit = doTest()

  def testViewBound(): Unit = doTest()
}

class FunctionParameterInfoInterleavedClausesTest extends FunctionParameterInfoTestBase {
  override def getTestDataPath: String =
    s"${super.getTestDataPath}typeParameters/"

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version.isScala3

  def testInterleavedTypeParameterClauses(): Unit = doTest()
}
