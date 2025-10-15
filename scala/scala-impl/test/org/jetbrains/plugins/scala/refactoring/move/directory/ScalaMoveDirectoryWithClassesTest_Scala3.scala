package org.jetbrains.plugins.scala.refactoring.move.directory

import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.extensions.PathExt

import java.nio.file.Path

class ScalaMoveDirectoryWithClassesTest_Scala3 extends ScalaMoveDirectoryWithClassesTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3

  override protected def getTestDataRoot: Path = super.getTestDataRoot / "scala3"

  // wildcard import in scala 3 is `*` instead of `_` so `after` directory is a bit different
  def testMovePackage(): Unit = doMovePackageTest()

  def testMovePackageWithToplevelFun(): Unit = doMovePackageTest("pack1.pack2", "pack1.pack3")

  def testRenamePackageWithMixedToplevelDefs(): Unit = doRenamePackageTest("pack1.pack2", "pack0.pack1.pack2")

  def testRenamePackageWithMultipleToplevelDefs(): Unit = doRenamePackageTest("pack1.pack2", "pack1.pack2.pack3")
}
