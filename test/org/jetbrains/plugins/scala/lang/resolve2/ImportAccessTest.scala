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
  //TODO
//  def testPrivateClass = doTest
  def testPrivateFunction = {}
  def testPrivateObject = {}
  def testPrivateTrait = {}
  def testPrivateValue = {}
  def testPrivateVariable = {}
}