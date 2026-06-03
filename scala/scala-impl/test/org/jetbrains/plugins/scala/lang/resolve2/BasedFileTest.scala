package org.jetbrains.plugins.scala.lang.resolve2

import org.jetbrains.plugins.scala.extensions.PathExt

import java.nio.file.Path

class BasedFileTest extends ResolveTestBase {
  override def folderPath: Path = super.folderPath / "dir"

  def testFileBased(): Unit = doTest()
}
