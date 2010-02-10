package org.jetbrains.plugins.scala.lang.resolve2


/**
 * Pavel.Fatin, 02.02.2010
 */

class FunctionPartialTest extends ResolveTestBase {
  override def getTestDataPath: String = {
    super.getTestDataPath + "function/partial/"
  }

  def testAppliedFirst = doTest
  def testAppliedMany = doTest
  def testAppliedSecond = doTest
  //TODO
//  def testOneToEmpty = doTest
  //TODO
//  def testOneToNone = doTest
  def testOneToOne = doTest
  //TODO
//  def testOneToTwo = doTest
  //TODO
//  def testTwoToOne = doTest
  def testTwoToTwo = doTest
  //TODO
//  def testTypeIncompatible = doTest
  def testTypeInheritance = doTest
  //TODO
//  def testTypeInheritanceIncompatible = doTest
}