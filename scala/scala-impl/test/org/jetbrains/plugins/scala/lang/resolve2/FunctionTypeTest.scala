package org.jetbrains.plugins.scala.lang.resolve2


/**
 * Pavel.Fatin, 02.02.2010
 */

class FunctionTypeTest extends ResolveTestBase {
  override def folderPath: String = {
    super.folderPath + "function/type/"
  }

  def testChoiceOne(): Unit = doTest()
  def testChoiceTwo(): Unit = doTest()
  def testIncompatible(): Unit = doTest()
  def testIncompatibleFirst(): Unit = doTest()
  def testIncompatibleSecond(): Unit = doTest()
  def testIncompatibleWithCount(): Unit = doTest()
  def testInheritanceChild(): Unit = doTest()
  def testInheritanceParent(): Unit = doTest()
  def testParentheses(): Unit = doTest()
}