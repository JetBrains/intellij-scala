package org.jetbrains.plugins.scala
package refactoring.extractMethod

class ScalaExtractMethodOutputTest extends ScalaExtractMethodTestBase {
  override def folderPath: String = super.folderPath + "output/"

  def testNoReturnNoOutput(): Unit = doTest()

  def testNoReturnOneOutput(): Unit = doTest()

  def testNoReturnSeveralOutput(): Unit = doTest()

  def testNoReturnUnitOutput(): Unit = doTest()

  def testReturnNoOutput(): Unit = doTest()

  def testReturnSeveralOutput1(): Unit = doTest()

  def testReturnSeveralOutput2(): Unit = doTest()

  def testUnitReturnNoOutput(): Unit = doTest()

  def testUnitReturnOneOutput(): Unit = doTest()

  def testUnitReturnSeveralOutput1(): Unit = doTest()

  def testUnitReturnSeveralOutput2(): Unit = doTest()

}
