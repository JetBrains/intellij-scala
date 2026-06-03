package org.jetbrains.plugins.scala.lang.resolve2

import org.jetbrains.plugins.scala.extensions.PathExt

import java.nio.file.Path

class ImportScopeTest extends ResolveTestBase {
  override def folderPath: Path = super.folderPath / "import" / "scope"

  def testBlock(): Unit = doTest()
  def testInnerBlock(): Unit = doTest()
  def testOuterBlockNested(): Unit = doTest()
  def testOuterBlock(): Unit = doTest()
  def testTwoBlocks(): Unit = doTest()
}
