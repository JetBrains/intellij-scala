package org.jetbrains.plugins.scala.lang.resolve2


/**
 * Pavel.Fatin, 02.02.2010
 */

class ImportAccessTest extends ResolveTestBase {
  override def folderPath: String = {
    super.folderPath + "import/access/"
  }

  def testInheritedPrivate() = doTest()
  def testInheritedPrivateClash1() = doTest()
//  def testInheritedPrivateClash2 = doTest
  def testPrivate() = doTest()
  def testPrivateClass() = doTest()
  def testPrivateClassAll() = doTest()
  def testPrivateFunction() = doTest()
  def testPrivateObject() = doTest()
  def testPrivateTrait() = doTest()
  def testPrivateValue() = doTest()
  def testPrivateVariable() = doTest()
  def testProtectedClass() = doTest()
}