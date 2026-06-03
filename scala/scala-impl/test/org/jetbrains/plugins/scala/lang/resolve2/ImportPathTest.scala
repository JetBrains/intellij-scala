package org.jetbrains.plugins.scala.lang.resolve2

import org.jetbrains.plugins.scala.extensions.PathExt

import java.nio.file.Path

class ImportPathTest extends ResolveTestBase {
  override def folderPath: Path = super.folderPath / "import" / "path"

  protected override def sourceRootPath: Path = folderPath

  def testDir(): Unit = doTest()
  //TODO ok
//  def testDirAndLocal = doTest
  def testDirThenLocal(): Unit = doTest()
  //TODO ok
//  def testTwoLocal = doTest
}
