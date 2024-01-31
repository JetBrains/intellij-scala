package org.jetbrains.plugins.scala.refactoring.move.directory

import org.jetbrains.plugins.scala.util.runners.{MultipleScalaVersionsRunner, RunWithScalaVersions, TestScalaVersion}
import org.junit.runner.RunWith

@RunWith(classOf[MultipleScalaVersionsRunner])
@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_2_12,
  TestScalaVersion.Scala_2_13,
))
class ScalaMoveDirectoryWithClassesTest_Scala2 extends ScalaMoveDirectoryWithClassesTestBase {
  def testMovePackage(): Unit = doMovePackageTest()
}
