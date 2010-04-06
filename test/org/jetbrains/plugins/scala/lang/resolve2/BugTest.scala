package org.jetbrains.plugins.scala.lang.resolve2

import org.jetbrains.plugins.scala.lang.resolve2.ResolveTestBase
import junit.framework.Assert


/**
 * Pavel.Fatin, 02.02.2010
 */

class BugTest extends ResolveTestBase {
  override def getTestDataPath: String = {
    super.getTestDataPath + "bug/"
  }
  def testBug1 = doTest

  //TODO answer?
//  def testIncomplete = doTest

  //TODO accessible
//  def testSimplePrivateAccess = doTest
  //TODO accessible
//  def testPrivateThis = doTest
  //TODO accessible
//  def testProtectedThis = doTest
  def testGetOrElse = doTest
  def testAnonymousClassMethods = doTest
  //TODO ok
//  def testIntegerEqualiity = doTest
  def testEarlyDefinitionsBefore = doTest
  def testFunctionEmptyParamList = doTest
}