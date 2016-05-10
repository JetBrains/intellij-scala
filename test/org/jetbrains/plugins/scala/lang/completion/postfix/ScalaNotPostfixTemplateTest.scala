package org.jetbrains.plugins.scala.lang.completion.postfix

/**
  * @author Roman.Shein
  * @since 05.05.2016.
  */
class ScalaNotPostfixTemplateTest extends PostfixTemplateTest {
  override def testPath(): String = super.testPath() + "not/"

  def testDoubleNot() = doTest()

  def testInMiddle() = doTest()

  def testParenthesized() = doTest()

  def testSimple() = doTest()

  def testSimplified() = doTest()

  def testUnknownType() = doTest()

  def testNotApplicable() = doNotApplicableTest()

  def testScl10247() = doTest()
}
