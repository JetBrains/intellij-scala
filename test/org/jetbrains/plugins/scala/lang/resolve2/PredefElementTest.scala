package org.jetbrains.plugins.scala.lang.resolve2


/**
 * Pavel.Fatin, 02.02.2010
 */

class PredefElementTest extends ResolveTestBase {
  override def getTestDataPath: String = {
    super.getTestDataPath + "predef/element/"
  }

  def testClass = doTest
  //TODO getClass
  def testCompanionObject = doTest
  def testFunction = doTest
  def testObject = doTest
  //TODO packageobject
  def testPackage = doTest
  def testTrait = doTest
  def testTypeAlias = doTest
}