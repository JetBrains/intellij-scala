package org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.negative

import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.ScalaAccessCanBePrivateInspectionTestBase

class Scala3NegativeAccessCanBePrivateTest extends ScalaAccessCanBePrivateInspectionTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean = version >= ScalaVersion.Latest.Scala_3_0

  def test_opaque_type(): Unit =
    checkTextHasNoErrors("object A { opaque type Foo = Int; val x: Foo = 1 }")

}
