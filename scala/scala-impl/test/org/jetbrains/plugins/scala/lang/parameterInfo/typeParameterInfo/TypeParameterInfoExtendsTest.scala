package org.jetbrains.plugins.scala.lang.parameterInfo.typeParameterInfo

class TypeParameterInfoExtendsTest extends TypeParameterInfoTestBase {

  override def getTestDataPath: String =
    s"${super.getTestDataPath}Extends/"

  def testAllBounds() = doTest()

  def testJavaGeneric() = doTest()

  def testScalaGenericExtends() = doTest()

  def testScalaLowerBound() = doTest()

  def testScalaViewBound() = doTest()
}