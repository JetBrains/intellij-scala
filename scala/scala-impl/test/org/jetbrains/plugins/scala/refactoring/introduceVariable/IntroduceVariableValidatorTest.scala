package org.jetbrains.plugins.scala
package refactoring
package introduceVariable

class IntroduceVariableValidatorTest extends AbstractIntroduceVariableValidatorTestBase("data") {

  override protected def getName(fileText: String): String = "value"
}
