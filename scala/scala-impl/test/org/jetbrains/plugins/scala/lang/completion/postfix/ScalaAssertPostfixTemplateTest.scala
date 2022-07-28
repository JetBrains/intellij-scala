package org.jetbrains.plugins.scala.lang.completion.postfix

class ScalaAssertPostfixTemplateTest extends PostfixTemplateTest {
  override def testPath(): String = super.testPath() + "assert/"

  def testAssert(): Unit = doTest()

  def testNotApplicable(): Unit = doNotApplicableTest()
}
