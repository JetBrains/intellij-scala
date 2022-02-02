package org.jetbrains.plugins.scala.lang.completion.postfix

class ScalaForEachPostfixTemplateTest extends PostfixTemplateTest {
  override def testPath() = super.testPath() + "foreach/"

  def testExample(): Unit = doTest()
}
