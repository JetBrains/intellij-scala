package org.jetbrains.plugins.scala

import org.jetbrains.plugins.scala.util.TestUtils

package object refactoring {
  private[refactoring]
  def refactoringCommonTestDataRoot: String = TestUtils.getTestDataPath + "/refactoring/"
}
