package org.jetbrains.plugins.scala.lang.resolve2

class FunctionCurryTest extends ResolveTestBase {
  override def folderPath: String = {
    super.folderPath + "function/curry/"
  }

  def testCurryiedToCurryied(): Unit = doTest()
  def testCurryiedToNormal(): Unit = doTest()
  def testNormalToCurryied(): Unit = doTest()
}