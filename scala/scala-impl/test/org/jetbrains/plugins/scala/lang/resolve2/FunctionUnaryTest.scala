package org.jetbrains.plugins.scala.lang.resolve2

import org.jetbrains.plugins.scala.extensions.PathExt

import java.nio.file.Path

class FunctionUnaryTest extends ResolveTestBase {
  override def folderPath: Path = super.folderPath / "function" / "unary"

  def testParenthesisedPrefix(): Unit = doTest()
  def testUnary(): Unit = doTest()
  def testUnaryIllegal(): Unit = doTest()
  def testUnaryParameter(): Unit = doTest()
}
