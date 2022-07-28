package org.jetbrains.plugins.scala.lang.resolve2

class FunctionAliasTest extends ResolveTestBase {
  override def folderPath: String = {
    super.folderPath + "function/alias/"
  }

  def testApply(): Unit = doTest()
  // TODO
//  def testCallThenApply = doTest
  //TODO how to be with syntetic method?
//  def testEquals = doTest
  //TODO how to be with syntetic method?
//  def testNotEquals = doTest
}