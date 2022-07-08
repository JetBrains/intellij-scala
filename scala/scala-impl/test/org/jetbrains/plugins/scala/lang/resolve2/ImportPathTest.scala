package org.jetbrains.plugins.scala.lang.resolve2

class ImportPathTest extends ResolveTestBase {
  override def folderPath: String = {
    super.folderPath + "import/path/"
  }

  protected override def sourceRootPath: String = folderPath

  def testDir(): Unit = doTest()
  //TODO ok
//  def testDirAndLocal = doTest
  def testDirThenLocal(): Unit = doTest()
  //TODO ok
//  def testTwoLocal = doTest
}