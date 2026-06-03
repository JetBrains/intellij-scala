package org.jetbrains.plugins.scala.lang.parameterInfo.functionParameterInfo

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
