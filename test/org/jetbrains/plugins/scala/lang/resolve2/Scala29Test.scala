package org.jetbrains.plugins.scala.lang.resolve2

import org.jetbrains.plugins.scala.util.TestUtils


class Scala29Test extends ResolveTestBase {
  override def getTestDataPath: String = {
    super.getTestDataPath + "scala29/"
  }

  override def scalaSdkVersion() = TestUtils.ScalaSdkVersion._2_9

  def testSCL2913 = doTest
}