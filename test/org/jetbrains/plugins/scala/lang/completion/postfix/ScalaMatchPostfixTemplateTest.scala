package org.jetbrains.plugins.scala.lang.completion.postfix

/**
  * Created by Roman.Shein on 10.05.2016.
  */
class ScalaMatchPostfixTemplateTest extends PostfixTemplateTest {
  override def testPath(): String = super.testPath() + "match/"

  def testSimple() = doTest()

  def testInnerMatch() = doTest()

  def testInfixExpr() = doTest()

  def testInInfixExpr() = doTest()

  def testInnerMatchInfixExpr() = doTest()
}
