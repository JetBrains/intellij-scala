package org.jetbrains.plugins.scala.codeInspection.unused.negative

import org.jetbrains.plugins.scala.codeInspection.unused.ScalaUnusedDeclarationInspectionTestBase

class Scala2SelfTypeTest extends ScalaUnusedDeclarationInspectionTestBase {

  def test_self_type(): Unit = checkTextHasNoErrors("@scala.annotation.unused trait Foo { bar: AnyVal => }")

}
