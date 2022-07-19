package org.jetbrains.plugins.scala
package refactoring
package introduceVariable

import junit.framework.{Test, TestCase}

class IntroduceVariableValidatorTest extends TestCase

object IntroduceVariableValidatorTest {
  def suite(): Test = new AbstractIntroduceVariableValidatorTestBase("data") {
    override protected def getName(fileText: String): String = "value"
  }
}
