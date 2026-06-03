package org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.negative

import org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.ScalaUnusedDeclarationInspectionTestBase

class Scala2SelfTypeTest extends ScalaUnusedDeclarationInspectionTestBase {

  def test_self_type(): Unit = checkTextHasNoErrors("@scala.annotation.unused trait Foo { bar: AnyVal => }")

}
