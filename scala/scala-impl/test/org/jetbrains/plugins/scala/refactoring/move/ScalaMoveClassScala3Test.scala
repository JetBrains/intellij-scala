package org.jetbrains.plugins.scala.refactoring.move

import org.jetbrains.plugins.scala.compilation.CompilerTestUtil.runWithErrorsFromCompiler
import org.jetbrains.plugins.scala.util.TestUtils
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

class ScalaMoveClassScala3Test extends ScalaMoveClassTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version >= LatestScalaVersions.Scala_3_0

  override protected def testDataRoot: String = TestUtils.getTestDataPath + "/moveScala3/"

  def testKeepImports(): Unit = {
    runWithErrorsFromCompiler(getProject)(doTest(Array("com.A"), "org"))
  }

}
