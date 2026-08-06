package org.jetbrains.plugins.scala.lang.resolve2

import org.jetbrains.plugins.scala.extensions.PathExt

import java.nio.file.Path

class ImportScopeClashTest extends ResolveTestBase {
  override def folderPath: Path = super.folderPath / "import" / "scope" / "clash"

  def testInnerBlock(): Unit = doTest()
  def testOuterBlock(): Unit = doTest()
  def testOuterBlockNested(): Unit = doTest()
  def testTwoBlocks(): Unit = doTest()
}
