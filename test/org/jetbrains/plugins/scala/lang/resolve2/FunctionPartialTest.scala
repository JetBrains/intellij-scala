package org.jetbrains.plugins.scala.lang.resolve2


/**
 * Pavel.Fatin, 02.02.2010
 */

class FunctionPartialTest extends ResolveTestBase {
  override def getTestDataPath: String = {
    super.getTestDataPath + "function/partial/"
  }

  def testAllToEmpty = doTest
  def testAllToNone = doTest
  def testAllToOne = doTest
  def testAllToTwo = doTest
  def testAppliedFirst = doTest
  def testAppliedMany = doTest
  def testAppliedSecond = doTest
  def testOneToEmpty = doTest
  def testOneToNone = doTest
  def testOneToOne = doTest
  def testOneToTwo = doTest
  def testTwoToOne = doTest
  def testTwoToTwo = doTest
  def testTypeIncompatible = doTest
  def testTypeInheritance = doTest
  def testTypeInheritanceIncompatible = doTest
}