package org.jetbrains.plugins.scala.settings

import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.annotator.HighlightingAdvisor
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.util.CompilerTestUtil.runWithErrorsFromCompiler
import org.junit.Assert.assertEquals

class ScalaHighlightingModeTest extends ScalaLightCodeInsightFixtureTestCase {

  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3_9

  // File name, contents, whether compiler highlighting supports this file in the module's Scala version.
  protected def fileCases: Seq[(String, String, Boolean)] = Seq(
    ("Example.scala", "class Example", true),
    ("worksheet.sc", "val x = 1", true),
    ("build.sbt", "name := \"example\"", false),
    ("build.mill", "package build", false),
    ("app/package.mill", "package build.app", false),
    ("build.mill.scala", "package build", false),
    ("Example.java", "class Example {}", version.isScala3),
    ("notes.txt", "text", false)
  )

  def testCompilerHighlightingEnabled(): Unit = checkFiles(compilerHighlightingEnabled = true)

  def testCompilerHighlightingDisabled(): Unit = checkFiles(compilerHighlightingEnabled = false)

  private def checkFiles(compilerHighlightingEnabled: Boolean): Unit = {
    def check(): Unit = fileCases.foreach { case (name, text, supportsCompilerHighlighting) =>
      val file = myFixture.addFileToProject(name, text)
      val compilerHighlighting = compilerHighlightingEnabled && supportsCompilerHighlighting
      val scala3CompilerErrors = compilerHighlighting && version.isScala3

      assertEquals(s"$name: compiler highlighting", compilerHighlighting,
        ScalaHighlightingMode.isShowErrorsFromCompilerEnabled(file))
      assertEquals(s"$name: Scala 3 compiler errors for the file", scala3CompilerErrors,
        ScalaHighlightingMode.showCompilerErrorsScala3(file))
      assertEquals(s"$name: Scala 3 compiler errors for an element", scala3CompilerErrors,
        ScalaHighlightingMode.showCompilerErrorsScala3(file.findElementAt(0)))

      if (file.isInstanceOf[ScalaFile]) {
        assertEquals(s"$name: type-aware annotator checks", !scala3CompilerErrors,
          HighlightingAdvisor.isTypeAwareHighlightingEnabled(file))
      }
    }

    if (compilerHighlightingEnabled) runWithErrorsFromCompiler(getProject)(check())
    else check()
  }
}

class ScalaHighlightingModeTest_Scala2 extends ScalaHighlightingModeTest {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_13
}
