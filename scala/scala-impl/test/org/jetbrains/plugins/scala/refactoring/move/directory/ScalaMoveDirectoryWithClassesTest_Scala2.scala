package org.jetbrains.plugins.scala.refactoring.move.directory

import org.jetbrains.plugins.scala.util.runners.{MultipleScalaVersionsJUnit4Runner, RunWithScalaVersions, TestScalaVersion}
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(classOf[MultipleScalaVersionsJUnit4Runner])
@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_2_12,
  TestScalaVersion.Scala_2_13
))
class ScalaMoveDirectoryWithClassesTest_Scala2 extends ScalaMoveDirectoryWithClassesTestBase {
  @Test
  def testMovePackage(): Unit = doMovePackageTest()

  @Test
  def testMovePackageRemoveUnresolvedImports(): Unit = doMovePackageTest()
}
