package org.jetbrains.plugins.scala.lang.resolve2


/**
 * Pavel.Fatin, 02.02.2010
 */

class InheritanceThisTest extends ResolveTestBase {
  override def getTestDataPath: String = {
    super.getTestDataPath + "inheritance/this/"
  }

  //TODO
//  def testThisBlock = doTest
  def testThisClass = doTest
  //TODO
//  def testThisFile = doTest
  //TODO
//  def testThisFunction = doTest
  def testThisObject = doTest
  def testThisTrait = doTest
}