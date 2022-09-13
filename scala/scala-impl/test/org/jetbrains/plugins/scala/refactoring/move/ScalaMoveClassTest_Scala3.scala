package org.jetbrains.plugins.scala.refactoring.move

import org.jetbrains.plugins.scala.util.CompilerTestUtil.runWithErrorsFromCompiler
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

class ScalaMoveClassTest_Scala3 extends ScalaMoveClassTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= LatestScalaVersions.Scala_3_0

  override protected def testDataRoot: String = super.testDataRoot + "/scala3/"

  def testKeepImports(): Unit = {
    runWithErrorsFromCompiler(getProject)(doTest(Seq("com.A"), "org"))
  }
}
