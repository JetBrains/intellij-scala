package org.jetbrains.plugins.scala.lang.parameterInfo.patternParameterInfo

class PatternParameterInfoUnapplyTest extends PatternParameterInfoTestBase {

  override def getTestDataPath: String =
    s"${super.getTestDataPath}unapply/"

  def testCompoundTypeField(): Unit = doTest()

  def testCompoundTypeParam(): Unit = doTest()

  def testUnapply(): Unit = doTest()

  def testUnapplySeq(): Unit = doTest()

  def testWithLocalTypeInference(): Unit = doTest()

  def testSelfType(): Unit = doTest()
}
