package org.jetbrains.plugins.scala.lang.types.existentialSimplification.generated

import org.jetbrains.plugins.scala.extensions.PathExt
import org.jetbrains.plugins.scala.lang.types.existentialSimplification.ExistentialSimplificationTestBase

import java.nio.file.Path

class ExistentialSimplificationAllRulesTest extends ExistentialSimplificationTestBase {
  override def folderPath: Path = super.folderPath / "allRules"

  def testAllRules(): Unit = doTest()
}
