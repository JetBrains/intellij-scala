package org.jetbrains.plugins.scala.lang.parameterInfo.patternParameterInfo

class PatternParameterInfoCaseClassesTest extends PatternParameterInfoTestBase {

  override def getTestDataPath: String =
    s"${super.getTestDataPath}caseClasses/"

  def testCaseClass(): Unit = doTest()

  def testCaseClassB(): Unit = doTest()

  def testAliasedPattern(): Unit = doTest()
}