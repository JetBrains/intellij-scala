package org.jetbrains.plugins.scala
package refactoring
package introduceVariable

import org.junit.Assert
import org.junit.runner.RunWith
import org.junit.runners.AllTests

@RunWith(classOf[AllTests])
class IntroduceVariableTypeValidatorTest extends AbstractIntroduceVariableValidatorTestBase("type") {

  override protected def getName(fileText: String): String = {
    if (!(fileText.indexOf("//") == 0)) Assert.assertTrue("Typename to validator should be in first comment statement.", false)
    fileText.substring(2, fileText.indexOf("\n")).replaceAll("\\W", "")
  }
}

object IntroduceVariableTypeValidatorTest {
  def suite() = new IntroduceVariableTypeValidatorTest
}
