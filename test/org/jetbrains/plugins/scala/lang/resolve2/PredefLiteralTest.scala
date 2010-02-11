package org.jetbrains.plugins.scala.lang.resolve2


/**
 * Pavel.Fatin, 02.02.2010
 */

class PredefLiteralTest extends ResolveTestBase {
  override def getTestDataPath: String = {
    super.getTestDataPath + "predef/literal/"
  }

  def testBoolean = doTest
  //TODO
//  def testByte = doTest
  //TODO
//  def testChar = doTest
  //TODO
//  def testDouble = doTest
  def testFloat = doTest
  //TODO
//  def testInt = doTest
  //TODO
//  def testLong = doTest
  //TODO
//  def testNull = doTest
  def testShort = doTest
  //TODO
//  def testString = doTest
  //TODO
//  def testSymbol = doTest
}