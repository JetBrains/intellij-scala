package org.jetbrains.plugins.scala.lang.resolve2

class OrderTest extends ResolveTestBase {
  override def folderPath: String = {
    super.folderPath + "order/"
  }

  def testBlock(): Unit = doTest()
  def testClass(): Unit = doTest()
  def testFile(): Unit = doTest()
  def testObject(): Unit = doTest()
  def testTrait(): Unit = doTest()
  def testOuterBlock(): Unit = doTest()
  def testOuterBlockNested(): Unit = doTest()
}