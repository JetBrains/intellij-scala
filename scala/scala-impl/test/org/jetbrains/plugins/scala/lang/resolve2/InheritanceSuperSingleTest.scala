package org.jetbrains.plugins.scala.lang.resolve2

class InheritanceSuperSingleTest extends ResolveTestBase {
  override def folderPath: String = {
    super.folderPath + "inheritance/super/single/"
  }

  def testClashClass(): Unit = doTest()
  def testClashObject(): Unit = doTest()
  def testClashTrait(): Unit = doTest()
  def testClass(): Unit = doTest()
  def testObject(): Unit = doTest()
  def testTrait(): Unit = doTest()
}