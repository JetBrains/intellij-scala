package org.jetbrains.plugins.scala.lang.resolve2


/**
 * Pavel.Fatin, 02.02.2010
 */

class FunctionTypeTest extends ResolveTestBase {
  override def getTestDataPath: String = {
    super.getTestDataPath + "function/type/"
  }

  def testChoiceOne = doTest
  def testChoiceTwo = doTest
  //TODO
//  def testIncompatible = doTest
  //TODO
//  def testIncompatibleFirst = doTest
  //TODO
//  def testIncompatibleSecond = doTest
  def testIncompatibleWithCount = doTest

  def testInheritanceChild = doTest
  
  //TODO
//  def testInheritanceParent = doTest

  def testParentheses = doTest
}