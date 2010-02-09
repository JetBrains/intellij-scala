package org.jetbrains.plugins.scala.lang.resolve2


/**
 * Pavel.Fatin, 02.02.2010
 */

class FunctionImplicitTest extends ResolveTestBase {
  override def getTestDataPath: String = {
    super.getTestDataPath + "function/implicit/"
  }

  def testCurrying = doTest
  def testCurryingPassAll = doTest
  //TODO
//  def testCurryingPassEmpty = doTest
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
}