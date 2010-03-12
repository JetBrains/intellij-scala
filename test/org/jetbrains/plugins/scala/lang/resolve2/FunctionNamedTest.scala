package org.jetbrains.plugins.scala.lang.resolve2


/**
 * Pavel.Fatin, 02.02.2010
 */

class FunctionNamedTest extends ResolveTestBase {
  override def getTestDataPath: String = {
    super.getTestDataPath + "function/named/"
  }

  def testClash1 = doTest
  def testClash2 = doTest
  def testClash3 = doTest
  def testExcess1 = doTest
  def testExcess2 = doTest
  def testNoName1 = doTest
  def testNoName2 = doTest
  def testOrderNormal = doTest
  def testOrderReverted = doTest
  def testUnnamed1 = doTest
  //TODO
//  def testUnnamed2 = doTest
}