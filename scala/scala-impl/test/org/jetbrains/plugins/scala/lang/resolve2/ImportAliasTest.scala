package org.jetbrains.plugins.scala.lang.resolve2

import org.jetbrains.plugins.scala.extensions.PathExt

import java.nio.file.Path

class ImportAliasTest extends ResolveTestBase {
  override def folderPath: Path = super.folderPath / "import" / "alias"

  //TODO importexclude
//  def testExclude = doTest
  //TODO importexclude
//  def testExcludeOnRename = doTest
  def testHide(): Unit = doTest()
  def testRename(): Unit = doTest()
}
