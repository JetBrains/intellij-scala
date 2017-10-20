package org.jetbrains.plugins.scala
package refactoring.extractMethod

/**
 * Nikolay.Tropin
 * 2014-04-21
 */
class ScalaExtractMethodOutputTest extends ScalaExtractMethodTestBase {
  override def folderPath: String = super.folderPath + "output/"

  def testNoReturnNoOutput() = doTest()

  def testNoReturnOneOutput() = doTest()

  def testNoReturnSeveralOutput() = doTest()

  def testNoReturnUnitOutput() = doTest()

  def testReturnNoOutput() = doTest()

  def testReturnSeveralOutput1() = doTest()

  def testReturnSeveralOutput2() = doTest()

  def testUnitReturnNoOutput() = doTest()

  def testUnitReturnOneOutput() = doTest()

  def testUnitReturnSeveralOutput1() = doTest()

  def testUnitReturnSeveralOutput2() = doTest()

}
