package org.jetbrains.plugins.scala.lang.resolve2

import org.jetbrains.plugins.scala.extensions.PathExt

import java.nio.file.Path

class FunctionAliasTest extends ResolveTestBase {
  override def folderPath: Path = super.folderPath / "function" / "alias"

  def testApply(): Unit = doTest()
  // TODO
//  def testCallThenApply = doTest
  //TODO how to be with syntetic method?
//  def testEquals = doTest
  //TODO how to be with syntetic method?
//  def testNotEquals = doTest
}
