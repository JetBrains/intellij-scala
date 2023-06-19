package org.jetbrains.plugins.scala.projectHighlighting.local

import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.testFramework.InspectionsKt
import org.jetbrains.plugins.scala.HighlightingTests
import org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.{ScalaAccessCanBeTightenedInspection, ScalaUnusedDeclarationInspection}
import org.jetbrains.plugins.scala.projectHighlighting.base.SbtProjectHighlightingLocalProjectsTestBase
import org.jetbrains.plugins.scala.projectHighlighting.reporter.HighlightingProgressReporter
import org.jetbrains.plugins.scala.util.RevertableChange
import org.junit.experimental.categories.Category

@Category(Array(classOf[HighlightingTests]))
abstract class SbtCrossBuildProjectHighlightingTestBase extends SbtProjectHighlightingLocalProjectsTestBase {

  override def projectName = "sbt-crossproject-test-project"

  override def setUp(): Unit = {
    super.setUp()

    codeInsightFixture.enableInspections(
      classOf[ScalaUnusedDeclarationInspection],
      classOf[ScalaAccessCanBeTightenedInspection],
    )

    //NOTE: java UnusedDeclarationInspection requires some special initialization in tests unlike most of the inspections
    val javaUnusedDeclarationInspection = new UnusedDeclarationInspection(true)
    InspectionsKt.enableInspectionTool(getProject, javaUnusedDeclarationInspection, getTestRootDisposable)
  }

  protected def withEnabledBackReferencesFromSharedSources(enabled: Boolean)(body: => Any): Unit = {
    val revertible = RevertableChange.withModifiedScalaProjectSettings[Boolean](
      getProject,
      _.isEnableBackReferencesFromSharedSources,
      _.setEnableBackReferencesFromSharedSources(_),
      enabled
    )
    revertible.run {
      body
    }
  }

  override protected def highlightSingleFile(
    virtualFile: VirtualFile,
    psiFile: PsiFile,
    reporter: HighlightingProgressReporter,
  ): Unit =
    doHighlightingForFile(virtualFile, psiFile, reporter)
}
