package org.jetbrains.plugins.scala.lang.resolve2

import org.jetbrains.plugins.scala.extensions.PathExt

import java.nio.file.Path

class FunctionRightTest extends ResolveTestBase {
  override def folderPath: Path = super.folderPath / "function" / "right"

  def testRight(): Unit = doTest()
  def testRightIllegal(): Unit = doTest()
}
