package org.jetbrains.plugins.scala.lang.completion.postfix

/**
  * @author Roman.Shein
  * @since 06.05.2016.
  */
class ScalaAssertPostfixTemplateTest extends PostfixTemplateTest {
  override def testPath(): String = super.testPath() + "assert/"

  def testAssert(): Unit = doTest()

  def testNotApplicable(): Unit = doNotApplicableTest()
}
