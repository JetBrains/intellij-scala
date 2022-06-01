package org.jetbrains.plugins.scala.codeInspection.unusedInspections.negative

import org.jetbrains.plugins.scala.codeInspection.unusedInspections.ScalaUnusedDeclarationInspectionTestBase

class Scala2SelfTypeTest extends ScalaUnusedDeclarationInspectionTestBase {

  def test_self_type(): Unit = checkTextHasNoErrors("@scala.annotation.unused trait Foo { bar: AnyVal => }")

}
