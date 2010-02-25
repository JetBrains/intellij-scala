package org.jetbrains.plugins.scala.lang.resolve2


/**
 * Pavel.Fatin, 02.02.2010
 */

class ImportAccessTest extends ResolveTestBase {
  override def getTestDataPath: String = {
    super.getTestDataPath + "import/access/"
  }

  //TODO
//  def testInheritedPrivate = doTest
  def testInheritedPrivateClash1 = doTest
  //TODO
//  def testInheritedPrivateClash2 = doTest
  //TODO
//  def testPrivate = doTest
  //TODO
//  def testPrivateClass = doTest
  //TODO
//  def testPrivateClassAll = doTest
  //TODO
//  def testPrivateFunction = doTest
  //TODO
//  def testPrivateObject = doTest
  //TODO
//  def testPrivateTrait = doTest
  //TODO
//  def testPrivateValue = doTest
  //TODO
//  def testPrivateVariable = doTest
  //TODO
//  def testProtectedClass = doTest
}