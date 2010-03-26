package org.jetbrains.plugins.scala.lang.resolve2

/**
 * Pavel.Fatin, 02.02.2010
 */

class FunctionImplicitTest extends ResolveTestBase {
  override def getTestDataPath: String = {
    super.getTestDataPath + "function/implicit/"
  }

  def testClashHierarchy = doTest
  //TODO implicitparameter
//  def testClashScope = doTest
  //TODO implicitparameter
//  def testClashValueAndImport1 = doTest
  //TODO implicitparameter
//  def testClashValueAndImport2 = doTest
  //TODO implicitparameter
//  def testClashValues = doTest
  def testContextBoundImplicit = doTest
  def testCurrying = doTest
  def testCurryingPassAll = doTest
  def testCurryingPassEmpty = doTest
  def testFunctionImplicit = doTest
  def testImportObjectImplicit = doTest
  def testImportObjectOrdinary = doTest
  def testPackageObjectImplicit = doTest
  def testPackageObjectImplicitChild = doTest
  //TODO implicitparameter
//  def testPackageObjectImplicitEmpty = doTest
  def testImportValueImplicit = doTest
  //TODO implicitparameter
//  def testImportValueOrdinary = doTest
  def testObjectImplicit = doTest
  //TODO implicitparameter
//  def testObjectOrdinary = doTest
  def testParameterImplicit = doTest
  def testTwoAsOne = doTest
  def testValueImplicit1 = doTest
  def testValueImplicit2 = doTest
  def testValueImplicitAsContextBound = doTest
  def testValueImplicitAsViewBound = doTest
  def testValueImplicitPass = doTest
  def testValueImplicitPassNone = doTest
  def testValueImplicitScope = doTest
  //TODO implicitparameter
//  def testValueNone = doTest
  //TODO implicitparameter
//  def testValueNoneAsContextBound = doTest
  //TODO implicitparameter
//  def testValueOrdinary = doTest
  def testVariableImplicit = doTest
  def testViewBoundImplicit = doTest
}