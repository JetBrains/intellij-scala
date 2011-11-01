package org.jetbrains.plugins.scala.lang.resolve2


/**
 * Pavel.Fatin, 02.02.2010
 */

class InheritanceAccessTest extends ResolveTestBase {
  override def folderPath: String = {
    super.folderPath + "inheritance/access/"
  }

  //TODO accessible
//  def testClashPrivateFunction = doTest
  def testClashProtectedFunction = doTest
  //TODO accessible
//  def testPrivateClass = doTest
  //TODO accessible
//  def testPrivateFunction = doTest
  def testProtectedFunction = doTest
}