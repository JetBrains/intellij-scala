package org.jetbrains.plugins.scala.lang.parameterInfo.typeParameterInfo

class TypeParameterInfoSimpleTestsTest extends TypeParameterInfoTestBase {

  override def getTestDataPath: String =
    s"${super.getTestDataPath}SimpleTests/"

  def testApplyMethodA(): Unit = doTest()

  // TODO
  // def testApplyMethodB = doTest

  def testContravariant(): Unit = doTest()

  def testCovariant(): Unit = doTest()

  def testFunDecl(): Unit = doTest()

  def testFunDef(): Unit = doTest()

  def testJavaMethod(): Unit = doTest()

  def testJustGeneric(): Unit = doTest()

  def testTypeAliasDef(): Unit = doTest()

  def testTypeParam(): Unit = doTest()

  def testAliasedClassTypeParams(): Unit = doTest()

  def testApplyFromVal(): Unit = doTest()

  def testInfixCall(): Unit = doTest()
}