package org.jetbrains.plugins.scala.lang.resolve2

import org.jetbrains.plugins.scala.extensions.PathExt

import java.nio.file.Path

class FunctionCurryTest extends ResolveTestBase {
  override def folderPath: Path = super.folderPath / "function" / "curry"

  def testCurryiedToCurryied(): Unit = doTest()
  def testCurryiedToNormal(): Unit = doTest()
  def testNormalToCurryied(): Unit = doTest()
}
