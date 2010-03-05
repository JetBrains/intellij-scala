package org.jetbrains.plugins.scala.lang.resolve2

import junit.framework.Assert


/**
 * Pavel.Fatin, 02.02.2010
 */

class ScopeAccessTest extends ResolveTestBase {
  override def getTestDataPath: String = {
    super.getTestDataPath + "scope/access/"
  }

  def testStub = Assert.assertTrue(true)

  //TODO accessible
//  def testPrivateCompanionClass = doTest
  //TODO accessible
//  def testPrivateCompanionObject = doTest
  //TODO accessible
//  def testPrivateThisCompanionClass = doTest
  //TODO accessible
//  def testPrivateThisCompanionObject = doTest
}