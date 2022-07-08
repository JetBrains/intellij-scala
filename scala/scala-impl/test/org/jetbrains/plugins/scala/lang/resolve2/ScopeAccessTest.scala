package org.jetbrains.plugins.scala.lang.resolve2

class ScopeAccessTest extends ResolveTestBase {
  override def folderPath: String = {
    super.folderPath + "scope/access/"
  }

  def testPrivateCompanionClass(): Unit = doTest()
  def testPrivateCompanionObject(): Unit = doTest()
  def testPrivateThisCompanionClass(): Unit = doTest()
  def testPrivateThisCompanionObject(): Unit = doTest()
}