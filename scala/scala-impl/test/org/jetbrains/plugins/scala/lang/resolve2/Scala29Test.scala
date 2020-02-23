package org.jetbrains.plugins.scala.lang.resolve2


class Scala29Test extends ResolveTestBase {
  override def folderPath: String = {
    super.folderPath + "scala29/"
  }

  def testSCL2913(): Unit = doTest()
}