package org.jetbrains.plugins.scala.lang.resolve2

class FunctionRightTest extends ResolveTestBase {
  override def folderPath: String = {
    super.folderPath + "function/right/"
  }

  def testRight(): Unit = doTest()
  def testRightIllegal(): Unit = doTest()
}