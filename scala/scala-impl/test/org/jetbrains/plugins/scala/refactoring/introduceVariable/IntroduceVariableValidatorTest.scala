package org.jetbrains.plugins.scala
package refactoring
package introduceVariable

import org.junit.runner.RunWith
import org.junit.runners.AllTests

@RunWith(classOf[AllTests])
class IntroduceVariableValidatorTest extends AbstractIntroduceVariableValidatorTestBase("data") {

  override protected def getName(fileText: String): String = "value"
}

object IntroduceVariableValidatorTest {
  def suite = new IntroduceVariableValidatorTest
}