package org.jetbrains.plugins.scala.lang.parameterInfo.typeParameterInfo

import org.jetbrains.plugins.scala.ScalaVersion

class TypeParameterInfoSimpleTestsTest extends TypeParameterInfoTestBase {

  override def getTestDataPath: String =
    s"${super.getTestDataPath}SimpleTests/"

  def testApplyMethodA(): Unit = doTest()

  def testApplyMethodB(): Unit = doTest()

  def testApplyTypeParams(): Unit = doTest()

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

class TypeParameterInfoInterleavedClausesTest extends TypeParameterInfoTestBase {
  override def getTestDataPath: String =
    s"${super.getTestDataPath}SimpleTests/"

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version.isScala3

  def testInterleavedTypeParameterClause(): Unit = doTest()

  def testInterleavedTypeParameterClauseAfterOmittedTypeArguments(): Unit = doTest()

  def testInterleavedTypeParameterClauseAfterOmittedUsingClause(): Unit = doTest()
}
