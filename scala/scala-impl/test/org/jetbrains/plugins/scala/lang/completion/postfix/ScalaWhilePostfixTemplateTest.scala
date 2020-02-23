package org.jetbrains.plugins.scala.lang.completion.postfix

/**
  * Created by Roman.Shein on 13.05.2016.
  */
class ScalaWhilePostfixTemplateTest extends PostfixTemplateTest {
  override def testPath(): String = super.testPath() + "while/"

  def testSimple(): Unit = doTest()
}
