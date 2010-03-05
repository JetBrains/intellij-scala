package org.jetbrains.plugins.scala.lang.resolve2


/**
 * Pavel.Fatin, 02.02.2010
 */

class ImportAccessTest extends ResolveTestBase {
  override def getTestDataPath: String = {
    super.getTestDataPath + "import/access/"
  }

  //TODO accessible
//  def testInheritedPrivate = doTest
  def testInheritedPrivateClash1 = doTest
  //TODO accessible
//  def testInheritedPrivateClash2 = doTest
  //TODO accessible
//  def testPrivate = doTest
  //TODO accessible
//  def testPrivateClass = doTest
  //TODO accessible
//  def testPrivateClassAll = doTest
  //TODO accessible
//  def testPrivateFunction = doTest
  //TODO accessible
//  def testPrivateObject = doTest
  //TODO accessible
//  def testPrivateTrait = doTest
  //TODO accessible
//  def testPrivateValue = doTest
  //TODO accessible
//  def testPrivateVariable = doTest
  //TODO accessible
//  def testProtectedClass = doTest
}