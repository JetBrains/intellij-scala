package org.jetbrains.plugins.scala.lang.resolve2

import junit.framework.Assert


/**
 * Pavel.Fatin, 02.02.2010
 */

class QualifierAccessTest extends ResolveTestBase {
  override def getTestDataPath: String = {
    super.getTestDataPath + "qualifier/access/"
  }

  def testStub = Assert.assertTrue(true)

  //TODO accessible
//  def testClassParameterValue = doTest
  //TODO accessible
//  def testClassParameterVariable = doTest
  //TODO accessible
//  def testPrivateRef = doTest
  //TODO accessible
//  def testPrivateRefCaseClass = doTest
  //TODO accessible
//  def testPrivateThis = doTest
  //TODO accessible
//  def testPrivateThisCaseClass = doTest
  //TODO accessible
//  def testSourcePrivate = doTest
  //TODO accessible
//  def testSourceProtected = doTest
  //TODO accessible
//  def testTargetPrivate = doTest
  //TODO accessible
//  def testTargetProtected = doTest
}