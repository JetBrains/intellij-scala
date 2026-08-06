package org.jetbrains.plugins.scala.lang.parameterInfo.typeParameterInfo

class TypeParameterInfoExtendsTest extends TypeParameterInfoTestBase {

  override def getTestDataPath: String =
    s"${super.getTestDataPath}Extends/"

  def testAllBounds(): Unit = doTest()

  def testJavaGeneric(): Unit = doTest()

  def testScalaGenericExtends(): Unit = doTest()

  def testScalaLowerBound(): Unit = doTest()

  def testScalaViewBound(): Unit = doTest()
}