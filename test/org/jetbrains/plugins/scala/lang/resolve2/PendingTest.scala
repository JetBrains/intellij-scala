package org.jetbrains.plugins.scala.lang.resolve2

class PendingTest extends ResolveTestBase {
  override def getTestDataPath: String = {
    super.getTestDataPath + "pending/"
  }

  def testSCL1701 = doTest

  def testSCL1707 = doTest

  def testSCL2408D = doTest  
}