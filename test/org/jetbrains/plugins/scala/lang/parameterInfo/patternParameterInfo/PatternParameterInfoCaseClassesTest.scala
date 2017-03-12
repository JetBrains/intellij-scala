package org.jetbrains.plugins.scala.lang.parameterInfo.patternParameterInfo

class PatternParameterInfoCaseClassesTest extends PatternParameterInfoTestBase {

  override def getTestDataPath: String =
    s"${super.getTestDataPath}caseClasses/"

  def testCaseClass() = doTest()

  def testCaseClassB() = doTest()

  def testAliasedPattern() = doTest()
}