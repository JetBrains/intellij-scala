package org.jetbrains.plugins.scala.lang.resolve2

import org.jetbrains.plugins.scala.extensions.PathExt

import java.nio.file.Path

class FunctionCountPriorityTest extends ResolveTestBase {
  override def folderPath: Path = super.folderPath / "function" / "count" / "priority"

  def testEmptyToAll(): Unit = doTest()
  def testNoneToAll(): Unit = doTest()
  def testOneToAll(): Unit = doTest()
  def testTwoToAll(): Unit = doTest()
}
