package org.jetbrains.plugins.scala.lang.resolve2

class UnresolvedTest extends ResolveTestBase {
  override def folderPath: String = {
    super.folderPath + "unresolved/"
  }

  def testNamedParameter(): Unit = doTest()
  def testFunction(): Unit = doTest()
  def testRef(): Unit = doTest()
  def testType(): Unit = doTest()
}