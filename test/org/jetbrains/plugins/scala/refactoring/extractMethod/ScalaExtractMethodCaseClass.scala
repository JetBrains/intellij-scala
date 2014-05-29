package org.jetbrains.plugins.scala
package refactoring.extractMethod

/**
 * Nikolay.Tropin
 * 2014-05-20
 */
class ScalaExtractMethodCaseClass extends ScalaExtractMethodTestBase {
  override def folderPath: String = super.folderPath + "caseClass/"

  def testNoReturnSeveralOutput() = doTest()

  def testReturnSeveralOutput1() = doTest()

  def testReturnSeveralOutput2() = doTest()

  def testUnitReturnSeveralOutput1() = doTest()

  def testUnitReturnSeveralOutput2() = doTest()
}
