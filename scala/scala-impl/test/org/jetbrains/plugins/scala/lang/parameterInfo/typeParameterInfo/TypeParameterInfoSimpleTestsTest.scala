package org.jetbrains.plugins.scala.lang.parameterInfo.typeParameterInfo

class TypeParameterInfoSimpleTestsTest extends TypeParameterInfoTestBase {

  override def getTestDataPath: String =
    s"${super.getTestDataPath}SimpleTests/"

  def testApplyMethodA() = doTest()

  // TODO
  // def testApplyMethodB = doTest

  def testContravariant() = doTest()

  def testCovariant() = doTest()

  def testFunDecl() = doTest()

  def testFunDef() = doTest()

  def testJavaMethod() = doTest()

  def testJustGeneric() = doTest()

  def testTypeAliasDef() = doTest()

  def testTypeParam() = doTest()

  def testAliasedClassTypeParams() = doTest()

  def testApplyFromVal() = doTest()

  def testInfixCall() = doTest()
}