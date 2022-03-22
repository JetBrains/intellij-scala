package org.jetbrains.plugins.scala.codeInspection.unused.negative

import org.jetbrains.plugins.scala.codeInspection.unused.ScalaUnusedSymbolInspectionTestBase

class Scala2SelfTypeTest extends ScalaUnusedSymbolInspectionTestBase {

  def test_self_type(): Unit = checkTextHasNoErrors("@scala.annotation.unused trait Foo { bar: AnyVal => }")

}
