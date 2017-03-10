package org.jetbrains.plugins.scala.lang.parameterInfo.patternParameterInfo

class PatternParameterInfoUnapplyTest extends PatternParameterInfoTestBase {

  override def getTestDataPath: String =
    s"${super.getTestDataPath}unapply/"

  def testCompoundTypeField() = doTest()

  def testCompoundTypeParam() = doTest()

  def testUnapply() = doTest()

  def testUnapplySeq() = doTest()

  def testWithLocalTypeInference() = doTest()

  def testSelfType() = doTest()
}
