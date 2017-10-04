package org.jetbrains.plugins.scala.lang.types.existentialSimplification.generated

import org.jetbrains.plugins.scala.lang.types.existentialSimplification.ExistentialSimplificationTestBase

class ExistentialSimplificationAllRulesTest extends ExistentialSimplificationTestBase {
  override def folderPath: String = super.folderPath + "allRules/"

  def testAllRules() = doTest()
}