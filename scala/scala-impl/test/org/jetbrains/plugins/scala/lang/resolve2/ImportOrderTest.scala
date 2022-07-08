package org.jetbrains.plugins.scala.lang.resolve2

class ImportOrderTest extends ResolveTestBase {
  override def folderPath: String = {
    super.folderPath + "import/order/"
  }

  def testBlock(): Unit = doTest()
  def testClass(): Unit = doTest()
  def testFile(): Unit = doTest()
  def testOuterBlockNested(): Unit = doTest()
  def testObject(): Unit = doTest()
  def testOuterBlock(): Unit = doTest()
  def testTrait(): Unit = doTest()
}