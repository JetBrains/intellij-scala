package org.jetbrains.plugins.scala.lang.resolve2


/**
 * Pavel.Fatin, 02.02.2010
 */

class PredefElementTest extends ResolveTestBase {
  override def getTestDataPath: String = {
    super.getTestDataPath + "predef/element/"
  }

  def testClass = doTest
  //TODO
//  def testCompanionObject = doTest
  def testFunction = doTest
  def testObject = doTest
  //TODO
//  def testPackage = doTest
  def testTrait = doTest
  def testTypeAlias = doTest
}