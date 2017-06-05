package org.jetbrains.plugins.scala
package refactoring
package introduceVariable

import org.junit.Assert

class IntroduceVariableTypeValidatorTest extends AbstractIntroduceVariableValidatorTestBase("type") {

  override protected def getName(fileText: String): String = {
    if (!(fileText.indexOf("//") == 0)) Assert.assertTrue("Typename to validator should be in first comment statement.", false)
    fileText.substring(2, fileText.indexOf("\n")).replaceAll("\\W", "")
  }
}
