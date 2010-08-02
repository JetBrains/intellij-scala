package org.jetbrains.plugins.scala.lang.resolve2

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

  def testCaseClassObjectStaticImport = doTest
  def testBufferPlusPlus = doTest

  def testCollectionExpression = doTest

  def testNamedConstructorParam = doTest
  def testNamedConstructorThisParam = doTest

  def testValueFunctionOverloading = doTest
  def testClassParameterResolve = doTest
  def testClassParameterResolveTwo = doTest
  def testAnnonymousFunctionUsage = doTest
  def testImplicitsApplicability = doTest

  def testImplicitChoose = doTest

  def testResolveEmpty = doTest
  def testOverloadedAction = doTest
  def testImplicitsInShapeIgnored = doTest

  def testInfixApply = doTest
  def testSCL2172 = doTest
  //TODO failed on buildserver
//  def testSCL2182 = doTest
  def testSCL2198 = doTest
  def testSCL2205 = doTest
  def testSCL2207 = doTest
  def testSCL2256 = doTest
  def testSCL1990 = doTest
  def testSCL2156 = doTest
  def testSCL1946 = doTest
  def testSCL2041 = doTest
  def testSCL2208 = doTest

  def testThisTypeSelfType = doTest
}