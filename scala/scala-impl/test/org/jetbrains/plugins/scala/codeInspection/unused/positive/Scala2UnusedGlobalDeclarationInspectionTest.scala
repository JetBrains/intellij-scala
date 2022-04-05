package org.jetbrains.plugins.scala.codeInspection.unused.positive

import org.jetbrains.plugins.scala.codeInspection.unused.ScalaUnusedDeclarationInspectionTestBase

class Scala2UnusedGlobalDeclarationInspectionTest extends ScalaUnusedDeclarationInspectionTestBase {
  def test_fail_to_prevent_merging(): Unit = {
    assert(false)
  }
}
