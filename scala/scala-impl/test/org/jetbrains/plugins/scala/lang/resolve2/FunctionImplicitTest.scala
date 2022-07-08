package org.jetbrains.plugins.scala.lang.resolve2

class FunctionImplicitTest extends ResolveTestBase {
  override def folderPath: String = {
    super.folderPath + "function/implicit/"
  }

  def testClashHierarchy(): Unit = doTest()
  //TODO implicitparameter
//  def testClashScope = doTest
  //TODO implicitparameter
//  def testClashValueAndImport1 = doTest
  //TODO implicitparameter
//  def testClashValueAndImport2 = doTest
  //TODO implicitparameter
//  def testClashValues = doTest
  def testContextBoundImplicit(): Unit = doTest()
  def testCurrying(): Unit = doTest()
  def testCurryingPassAll(): Unit = doTest()
  def testCurryingPassEmpty(): Unit = doTest()
  def testFunctionImplicit(): Unit = doTest()
  def testImportObjectImplicit(): Unit = doTest()
  def testImportObjectOrdinary(): Unit = doTest()
  def testPackageObjectImplicit(): Unit = doTest()
  def testPackageObjectImplicitChild(): Unit = doTest()
  //TODO implicitparameter
//  def testPackageObjectImplicitEmpty = doTest
  def testImportValueImplicit(): Unit = doTest()
  //TODO implicitparameter
//  def testImportValueOrdinary = doTest
  def testNoScope(): Unit = doTest()
  def testObjectImplicit(): Unit = doTest()
  //TODO implicitparameter
//  def testObjectOrdinary = doTest
  def testParameterImplicit(): Unit = doTest()
  def testTwoAsOne(): Unit = doTest()
  def testValueImplicit1(): Unit = doTest()
  def testValueImplicit2(): Unit = doTest()
  def testValueImplicitAsContextBound(): Unit = doTest()
  def testValueImplicitAsViewBound(): Unit = doTest()
  def testValueImplicitPass(): Unit = doTest()
  def testValueImplicitPassNone(): Unit = doTest()
  def testValueImplicitScope(): Unit = doTest()
  //TODO implicitparameter
//  def testValueNone = doTest
  //TODO implicitparameter
//  def testValueNoneAsContextBound = doTest
  //TODO implicitparameter
//  def testValueOrdinary = doTest
  def testVariableImplicit(): Unit = doTest()
  def testViewBoundImplicit(): Unit = doTest()
}