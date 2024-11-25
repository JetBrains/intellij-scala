package org.jetbrains.plugins.scala
package refactoring
package introduceVariable

import junit.framework.{Test, TestCase}

class IntroduceVariableTest extends TestCase

object IntroduceVariableTest {
  private val DATA_PATH = "/refactoring/introduceVariable/data"

  def suite(): Test = new IntroduceVariableTestSuite(DATA_PATH, ScalaLanguage.INSTANCE)
}


class IntroduceVariableScala3Test extends TestCase

object IntroduceVariableScala3Test {
  private val DATA_PATH = "/refactoring/introduceVariable/data3"

  def suite(): Test = new IntroduceVariableTestSuite(DATA_PATH, Scala3Language.INSTANCE)
}
