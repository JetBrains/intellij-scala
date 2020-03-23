package org.jetbrains.plugins.scala.refactoring.move
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.util.TestUtils

class ScalaMoveClassScala3Test extends ScalaMoveClassTestBase {
  override protected def supportedIn(version: ScalaVersion) = version >= ScalaVersion.Scala_3_0

  override protected def testDataRoot = TestUtils.getTestDataPath + "/moveScala3/"

  def testKeepImports(): Unit = {
    doTest(Array("com.A"), "org")
  }

}
