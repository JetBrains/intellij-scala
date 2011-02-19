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
  def testSCL2384A = doTest
  def testSCL2384B = doTest
  def testSCL2384C = doTest
  def testSCL2390 = doTest
  def testSCL2390B = doTest
  def testSCL2408A = doTest
  def testSCL2408B = doTest
  def testSCL2408C = doTest
  def testSCL2452 = doTest
  def testSCL1929 = doTest
  def testSCL2418 = doTest
  def testSCL2529 = doTest
  def testSCL2039 = doTest
  def testSCL2722 = doTest

  def testDependent = doTest
  def testDependentEquality = doTest
}