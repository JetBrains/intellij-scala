package org.jetbrains.plugins.scala.lang.completion.postfix

import com.intellij.testFramework.NeedsIndex

@NeedsIndex.SmartMode(reason = "`assert` needs type inference to check conformance with Boolean")
class ScalaAssertPostfixTemplateTest extends PostfixTemplateTest {
  override def testPath(): String = super.testPath() + "assert/"

  def testAssert(): Unit = doTest()

  def testNotApplicable(): Unit = doNotApplicableTest()
}
