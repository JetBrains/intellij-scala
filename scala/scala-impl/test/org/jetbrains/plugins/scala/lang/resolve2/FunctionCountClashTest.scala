package org.jetbrains.plugins.scala.lang.resolve2

import org.jetbrains.plugins.scala.extensions.PathExt

import java.nio.file.Path

class FunctionCountClashTest extends ResolveTestBase {
  override def folderPath: Path =
    super.folderPath / "function" / "count" / "clash"

//  def testEmptyAndNone = doTest
  def testOneAndEmpty(): Unit = doTest()
  def testOneAndNone(): Unit = doTest()
  def testOneAndTwo(): Unit = doTest()
}
