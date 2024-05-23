package org.jetbrains.plugins.scala.lang.completion.postfix

import com.intellij.testFramework.NeedsIndex

@NeedsIndex.SmartMode(reason = "`notnull` needs type inference to check conformance with AnyRef")
class ScalaNotNullPostfixTemplateTest extends PostfixTemplateTest {
  override def testPath(): String = super.testPath() + "notnull/"

  def testChain(): Unit = doTest()

  def testInfix(): Unit = doTest()

  def testMethodCall(): Unit = doTest()

  def testNotApplicableBoolean(): Unit = doNotApplicableTest()

  def testNotApplicableInt(): Unit = doNotApplicableTest()

  def testParenthesized(): Unit = doTest()

  def testSimple(): Unit = doTest()
}
