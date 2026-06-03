package org.jetbrains.plugins.scala

import org.jetbrains.plugins.scala.util.TestUtils

import java.nio.file.Path

package object refactoring {
  private[refactoring]
  def refactoringCommonTestDataRoot: Path = Path.of(TestUtils.getTestDataPath, "refactoring")
}
