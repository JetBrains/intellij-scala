package org.jetbrains.plugins.scala.lang.resolve2

import org.jetbrains.plugins.scala.extensions.PathExt

import java.nio.file.Path

class ImportOrderTest extends ResolveTestBase {
  override def folderPath: Path = super.folderPath / "import" / "order"

  def testBlock(): Unit = doTest()
  def testClass(): Unit = doTest()
  def testFile(): Unit = doTest()
  def testOuterBlockNested(): Unit = doTest()
  def testObject(): Unit = doTest()
  def testOuterBlock(): Unit = doTest()
  def testTrait(): Unit = doTest()
}
