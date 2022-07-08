package org.jetbrains.plugins.scala.lang.resolve2

class ImportAccessTest extends ResolveTestBase {
  override def folderPath: String = {
    super.folderPath + "import/access/"
  }

  def testInheritedPrivate(): Unit = doTest()
  def testInheritedPrivateClash1(): Unit = doTest()
//  def testInheritedPrivateClash2 = doTest
  def testPrivate(): Unit = doTest()
  def testPrivateClass(): Unit = doTest()
  def testPrivateClassAll(): Unit = doTest()
  def testPrivateFunction(): Unit = doTest()
  def testPrivateObject(): Unit = doTest()
  def testPrivateTrait(): Unit = doTest()
  def testPrivateValue(): Unit = doTest()
  def testPrivateVariable(): Unit = doTest()
  def testProtectedClass(): Unit = doTest()
}