package org.jetbrains.plugins.scala.refactoring.move

import org.jetbrains.plugins.scala.ScalaVersion

final class ScalaMoveClassTest_Scala213 extends ScalaMoveClassTestBase {

  override protected def getTestDataRoot: String = super.getTestDataRoot + "/scala213/"

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version == ScalaVersion.Latest.Scala_2_13

  //SCL-19801
  def testMoveClass_NameClashesWithOtherNamesImportedFromOtherPackageWithWithWildcard(): Unit = {
    doTest(
      Seq(
        "org.example.declaration.Random",
        "org.example.declaration.X",
      ),
      "org.example.declaration.data"
    )
  }
}
