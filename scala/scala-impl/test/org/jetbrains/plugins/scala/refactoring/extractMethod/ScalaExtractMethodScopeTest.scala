package org.jetbrains.plugins.scala.refactoring.extractMethod

class ScalaExtractMethodScopeTest extends ScalaExtractMethodTestBase {
  override def folderPath: String = super.folderPath + "scope/"

  def testNewClassScope() = doTest()
  def testNewClassScope2() = doTest()
  def testNewClassScope3() = doTest()
  def testNewClassScope4() = doTest()
  def testNewClassScope5() = doTest()
  def testNewClassScope6() = doTest()
}
