package org.jetbrains.plugins.scala.lang.resolve2

import org.jetbrains.plugins.scala.util.TestUtils
import org.jetbrains.plugins.scala.util.TestUtils.ScalaSdkVersion


class Scala29Test extends ResolveTestBase {
  override def folderPath: String = {
    super.folderPath + "scala29/"
  }

  def testSCL2913 = doTest

  def testSCL3212 = doTest
}