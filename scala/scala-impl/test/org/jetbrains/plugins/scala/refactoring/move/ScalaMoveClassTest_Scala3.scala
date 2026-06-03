package org.jetbrains.plugins.scala.refactoring.move

import org.jetbrains.plugins.scala.extensions.PathExt
import org.jetbrains.plugins.scala.util.CompilerTestUtil.runWithErrorsFromCompiler
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

import java.nio.file.Path

class ScalaMoveClassTest_Scala3 extends ScalaMoveClassTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= LatestScalaVersions.Scala_3_0

  override protected def getTestDataRoot: Path = super.getTestDataRoot / "scala3"

  def testKeepImportsWhenCBHIsEnabled(): Unit = {
    runWithErrorsFromCompiler(getProject) {
      doTest(Seq("com.A"), "org")
    }
  }

  def testDontKeepImportsWhenCBHIsDisabled(): Unit = {
    doTest(Seq("com.A"), "org")
  }

  def testWithTopLevelDefsInFile_MoveClass(): Unit = {
    doTest(Seq("MyClass"), "")
  }
}
