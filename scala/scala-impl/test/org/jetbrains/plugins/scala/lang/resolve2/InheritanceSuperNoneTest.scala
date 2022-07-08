package org.jetbrains.plugins.scala.lang.resolve2

class InheritanceSuperNoneTest extends ResolveTestBase {
  override def folderPath: String = {
    super.folderPath + "inheritance/super/none/"
  }

  def testBlock(): Unit = doTest()
  def testClass(): Unit = doTest()
  def testFile(): Unit = doTest()
  def testFunction(): Unit = doTest()
  def testObject(): Unit = doTest()
  def testTrait(): Unit = doTest()
}