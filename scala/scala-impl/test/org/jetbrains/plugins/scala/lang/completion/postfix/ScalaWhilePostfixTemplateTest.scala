package org.jetbrains.plugins.scala.lang.completion.postfix

class ScalaWhilePostfixTemplateTest extends PostfixTemplateTest {
  override def testPath(): String = super.testPath() + "while/"

  def testSimple(): Unit = doTest()
}
