package org.jetbrains.plugins.scala.lang.resolve2


/**
 * Pavel.Fatin, 02.02.2010
 */

class InheritanceAccessTest extends ResolveTestBase {
  override def getTestDataPath: String = {
    super.getTestDataPath + "inheritance/access/"
  }

  //TODO
//  def testClashPrivateFunction = doTest
  def testClashProtectedFunction = doTest
  //TODO
//  def testPrivateClass = doTest
  //TODO
//  def testPrivateFunction = doTest
  def testProtectedFunction = doTest
}