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

  //TODO
//  def testClassParameterValue = doTest
  //TODO
//  def testClassParameterVariable = doTest
  //TODO
//  def testPrivateRef = doTest
  //TODO
//  def testPrivateRefCaseClass = doTest
  //TODO
//  def testPrivateThis = doTest
  //TODO
//  def testPrivateThisCaseClass = doTest
  //TODO
//  def testSourcePrivate = doTest
  //TODO
//  def testSourceProtected = doTest
  //TODO
//  def testTargetPrivate = doTest
  //TODO
//  def testTargetProtected = doTest
}