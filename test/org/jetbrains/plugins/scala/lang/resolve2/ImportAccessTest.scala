package org.jetbrains.plugins.scala.lang.resolve2


/**
 * Pavel.Fatin, 02.02.2010
 */

class ImportAccessTest extends ResolveTestBase {
  override def getTestDataPath: String = {
    super.getTestDataPath + "import/access/"
  }

  //TODO
//  def testPrivate = doTest
  def testPrivateClass = doTest
  def testPrivateFunction = doTest
  def testPrivateObject = doTest
  def testPrivateTrait = doTest
  def testPrivateValue = doTest
  def testPrivateVariable = doTest
}