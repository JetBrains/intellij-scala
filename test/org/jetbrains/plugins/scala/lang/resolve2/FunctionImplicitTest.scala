package org.jetbrains.plugins.scala.lang.resolve2


/**
 * Pavel.Fatin, 02.02.2010
 */

class FunctionImplicitTest extends ResolveTestBase {
  override def getTestDataPath: String = {
    super.getTestDataPath + "function/implicit/"
  }

  def testValueImplicit1 = doTest
  def testValueImplicit2 = doTest
  //TODO
//  def testValueNone = doTest
  //TODO
//  def testValueOrdinary = doTest
}