package org.jetbrains.plugins.scala.lang.resolve2

class BasedFileTest extends ResolveTestBase {
  override def folderPath: String = {
    super.folderPath + "dir/"
  }

  def testFileBased(): Unit = doTest()
}