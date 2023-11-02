package org.jetbrains.plugins.scala
package refactoring
package introduceVariable

import junit.framework.{Test, TestCase}

class IntroduceVariableTest extends TestCase

object IntroduceVariableTest {
  private val DATA_PATH = "/refactoring/introduceVariable/data"

  def suite(): Test = new IntroduceVariableTestSuite(IntroduceVariableTest.DATA_PATH)
}
