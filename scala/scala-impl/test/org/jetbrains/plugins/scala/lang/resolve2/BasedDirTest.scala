package org.jetbrains.plugins.scala.lang.resolve2

import org.jetbrains.plugins.scala.extensions.PathExt

import java.nio.file.Path

class BasedDirTest extends ResolveTestBase {
  override def folderPath: Path = super.folderPath / "dir"

  def testDirBased(): Unit = doTest()
}
