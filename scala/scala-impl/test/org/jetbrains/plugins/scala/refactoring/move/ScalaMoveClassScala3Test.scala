package org.jetbrains.plugins.scala.refactoring.move
import org.jetbrains.plugins.scala.util.TestUtils
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

class ScalaMoveClassScala3Test extends ScalaMoveClassTestBase {
  override protected def supportedIn(version: ScalaVersion) = version >= LatestScalaVersions.Scala_3_0

  override protected def testDataRoot = TestUtils.getTestDataPath + "/moveScala3/"

  def testKeepImports(): Unit = {
    doTest(Array("com.A"), "org")
  }

}
