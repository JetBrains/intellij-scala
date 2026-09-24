package org.jetbrains.plugins.scala.refactoring.move

import org.jetbrains.plugins.scala.extensions.PathExt
import org.jetbrains.plugins.scala.util.CompilerTestUtil.runWithErrorsFromCompiler
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

import java.nio.file.Path

@RunWith(classOf[JUnit4])
class ScalaMoveClassTest_Scala3 extends ScalaMoveClassTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= LatestScalaVersions.Scala_3_0

  override protected def getTestDataRoot: Path = super.getTestDataRoot / "scala3"

  @Test
  def testKeepImportsWhenCBHIsEnabled(): Unit = {
    runWithErrorsFromCompiler(getProject) {
      doTest(Seq("com.A"), "org")
    }
  }

  @Test
  def testDontKeepImportsWhenCBHIsDisabled(): Unit = {
    doTest(Seq("com.A"), "org")
  }

  @Test
  def testWithTopLevelDefsInFile_MoveClass(): Unit = {
    doTest(Seq("MyClass"), "")
  }
}
