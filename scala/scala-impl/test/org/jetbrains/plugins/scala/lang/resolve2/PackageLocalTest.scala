package org.jetbrains.plugins.scala
package lang.resolve2

import org.jetbrains.plugins.scala.extensions.PathExt

import java.nio.file.Path

abstract class PackageLocalTestBase extends ResolveTestBase {
  override def folderPath: Path = super.folderPath / "element" / "packagelocalclash"
}

class PackageLocalTest_before_Scala2_12 extends PackageLocalTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean =
    version <= LatestScalaVersions.Scala_2_12

  def testC(): Unit = doTest()
}

class PackageLocalTest_after_Scala2_13 extends PackageLocalTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean =
    version > LatestScalaVersions.Scala_2_12

  def testCScala213(): Unit = doTest()
}
