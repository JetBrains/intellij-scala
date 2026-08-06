package org.jetbrains.plugins.scala
package refactoring
package introduceVariable

import org.junit.Assert.assertTrue

class IntroduceVariableTypeValidatorTest extends AbstractIntroduceVariableValidatorTestBase("type") {
  override protected def getName(fileText: String): String = {
    assertTrue("Typename to validator should be in first comment statement.", fileText.indexOf("//") == 0)
    fileText.substring(2, fileText.indexOf("\n")).replaceAll("\\W", "")
  }
}
