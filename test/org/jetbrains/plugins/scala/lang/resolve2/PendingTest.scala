package org.jetbrains.plugins.scala.lang.resolve2

class PendingTest extends ResolveTestBase {
  override def getTestDataPath: String = {
    super.getTestDataPath + "pending/"
  }

  def testSCL1701 = doTest

  def testSCL1707 = doTest

  def testSCL2073 = doTest

  def testSCL2386A = doTest

  def testSCL2386B = doTest

  def testSCL2386C = doTest

  def testSCL2386D = doTest

  def testSCL2386E = doTest

  def testSCL2418 = doTest

  def testSCL2456 = doTest
}
