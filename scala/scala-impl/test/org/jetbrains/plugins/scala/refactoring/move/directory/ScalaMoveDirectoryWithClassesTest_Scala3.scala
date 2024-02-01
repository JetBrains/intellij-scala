package org.jetbrains.plugins.scala.refactoring.move.directory

import org.jetbrains.plugins.scala.util.runners.{MultipleScalaVersionsRunner, RunWithScalaVersions, TestScalaVersion}
import org.junit.runner.RunWith

@RunWith(classOf[MultipleScalaVersionsRunner])
@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_3_Latest,
))
class ScalaMoveDirectoryWithClassesTest_Scala3 extends ScalaMoveDirectoryWithClassesTestBase {
  override protected def getTestDataRoot: String = super.getTestDataRoot + "scala3/"

  // wildcard import in scala 3 is `*` instead of `_` so `after` directory is a bit different
  def testMovePackage(): Unit = doMovePackageTest()

  // TODO(SCL-21988): remove unresolved imports (see `import pack1.pack2.*` in Usage.scala)
  def _testMovePackageWithToplevelFun(): Unit = doMovePackageTest(sourcePackageName = "pack1.pack2", "pack1.pack3")

  // TODO(SCL-21988): remove unresolved imports (see `import pack1.pack2.*` in Usage.scala)
  def _testRenamePackageWithMixedToplevelDefs(): Unit = doRenamePackageTest("pack1.pack2", "pack0.pack1.pack2")

  def testRenamePackageWithMultipleToplevelDefs(): Unit = doRenamePackageTest("pack1.pack2", "pack1.pack2.pack3")
}
