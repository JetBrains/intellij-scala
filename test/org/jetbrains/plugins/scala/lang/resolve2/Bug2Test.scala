package org.jetbrains.plugins.scala.lang.resolve2

/**
 * @author Alexander Podkhalyuzin
 */

class Bug2Test extends ResolveTestBase {
  override def getTestDataPath: String = {
    super.getTestDataPath + "bug2/"
  }

  def testSCL2087 = doTest
  def testSCL2268 = doTest
  def testSCL2293 = doTest
  def testSCL2120 = doTest
  def testSCL2295 = doTest
  def testDependent = doTest
}