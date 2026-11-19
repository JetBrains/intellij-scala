package org.jetbrains.plugins.scala.refactoring.move

import org.jetbrains.plugins.scala.ScalaVersion

final class ScalaMoveClassAstRetentionTest_Scala213 extends ScalaMoveClassAstRetentionTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version == ScalaVersion.Latest.Scala_2_13
}
