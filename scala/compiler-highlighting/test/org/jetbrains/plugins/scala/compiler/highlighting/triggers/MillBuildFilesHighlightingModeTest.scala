package org.jetbrains.plugins.scala.compiler.highlighting.triggers

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.{PsiFile, PsiManager}
import org.jetbrains.plugins.scala.annotator.HighlightingAdvisor
import org.jetbrains.plugins.scala.compiler.highlighting.services.core.CompilationStateWritesTestBase
import org.jetbrains.plugins.scala.extensions.inReadAction
import org.jetbrains.plugins.scala.settings.ScalaHighlightingMode
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}
import org.junit.Assert.{assertFalse, assertTrue}
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Mill build files are left to the annotator, while compiler-based highlighting stays on for the sources beside them.
 *
 * Scala 3, because there the annotator also stands aside for every file of the module while compiler-based
 * highlighting is on.
 */
@RunWith(classOf[JUnit4])
class MillBuildFilesHighlightingModeTest extends CompilationStateWritesTestBase:

  override protected def supportedIn(version: ScalaVersion): Boolean = version >= LatestScalaVersions.Scala_3

  private def psiOf(file: VirtualFile): PsiFile = PsiManager.getInstance(getProject).findFile(file)

  private def compilerHighlightingFor(file: VirtualFile): Boolean =
    inReadAction(ScalaHighlightingMode.isShowErrorsFromCompilerEnabled(psiOf(file)))

  private def annotatorChecksTypesIn(file: VirtualFile): Boolean =
    inReadAction(HighlightingAdvisor.isTypeAwareHighlightingEnabled(psiOf(file)))

  private def millBuildFiles: Seq[VirtualFile] = Seq(
    inSources("build.mill", "package build"),
    inSources("app/package.mill", "package build.app"),
    inSources("build.mill.scala", "package build"),
  )

  @Test
  def testMillBuildFilesAreLeftToTheAnnotator(): Unit =
    millBuildFiles.foreach { file =>
      assertFalse(s"no compiler-based highlighting in ${file.getName}", compilerHighlightingFor(file))
      assertTrue(s"the annotator checks types in ${file.getName}", annotatorChecksTypesIn(file))
    }

  @Test
  def testScalaSourcesKeepCompilerHighlighting(): Unit =
    val file = inSources("Regular.scala", "class Regular")
    assertTrue(compilerHighlightingFor(file))
    assertFalse("the annotator leaves types to the compiler", annotatorChecksTypesIn(file))
