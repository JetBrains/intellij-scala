package org.jetbrains.plugins.scala.lang.resolve2


/**
 * Pavel.Fatin, 02.02.2010
 */

class FunctionDefaultTest extends ResolveTestBase {
  override def getTestDataPath: String = {
    super.getTestDataPath + "function/default/"
  }

  def testFirstAsOne = doTest
  def testFirstAsTwo = doTest
  def testImplicitExists = doTest
  def testImplicitNotExists = doTest
  def testOneAsEmpty = doTest
  def testOneAsIncompatible = doTest
  def testOneAsNone = doTest
  def testOneAsOne = doTest
  def testOneAsTwo = doTest
  def testSecondAsEmpty = doTest
  def testSecondAsNone = doTest
  def testSecondAsOne = doTest
  def testSecondAsThree = doTest
  def testSecondAsTwo = doTest
  def testTwoAsEmpty = doTest
  def testTwoAsNone = doTest
  def testTwoAsOne = doTest
  def testTwoAsThree = doTest
  def testTwoAsTwo = doTest
}