package org.jetbrains.plugins.scala.lang.resolve2

class InheritanceAccessTest extends ResolveTestBase {
  override def folderPath: String = {
    super.folderPath + "inheritance/access/"
  }

  def testClashPrivateFunction(): Unit = doTest()
  def testClashProtectedFunction(): Unit = doTest()
  def testPrivateClass(): Unit = doTest()
  def testPrivateFunction(): Unit = doTest()
  def testProtectedFunction(): Unit = doTest()
}