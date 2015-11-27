package org.jetbrains.plugins.scala.lang.resolve2


/**
 * Pavel.Fatin, 02.02.2010
 */

class FunctionTypeTest extends ResolveTestBase {
  override def folderPath: String = {
    super.folderPath + "function/type/"
  }

  def testChoiceOne() = doTest()
  def testChoiceTwo() = doTest()
  def testIncompatible() = doTest()
  def testIncompatibleFirst() = doTest()
  def testIncompatibleSecond() = doTest()
  def testIncompatibleWithCount() = doTest()
  def testInheritanceChild() = doTest()
  def testInheritanceParent() = doTest()
  def testParentheses() = doTest()
}