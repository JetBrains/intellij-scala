package org.jetbrains.plugins.scala.lang.resolve2


/**
 * Pavel.Fatin, 02.02.2010
 */

class FunctionImplicitTest extends ResolveTestBase {
  override def getTestDataPath: String = {
    super.getTestDataPath + "function/implicit/"
  }

  //TODO
//  def testClash = doTest
  def testClashHierarchy = doTest
  def testCurrying = doTest
  def testCurryingPassAll = doTest
  //TODO
//  def testCurryingPassEmpty = doTest
  def testFunctionImplicit = doTest
  def testImportObjectImplicit = doTest
  //TODO
//  def testImportObjectOrdinary = doTest
  def testImportValueImplicit = doTest
  //TODO
//  def testImportValueOrdinary = doTest
  def testObjectImplicit = doTest
  //TODO
//  def testObjectOrdinary = doTest
  def testTwoAsOne = doTest
  def testValueImplicit1 = doTest
  def testValueImplicit2 = doTest
  def testValueImplicitPass = doTest
  //TODO
//  def testValueImplicitPassNone = doTest
  //TODO
//  def testValueNone = doTest
  //TODO
//  def testValueOrdinary = doTest
  def testVariableImplicit = doTest
}