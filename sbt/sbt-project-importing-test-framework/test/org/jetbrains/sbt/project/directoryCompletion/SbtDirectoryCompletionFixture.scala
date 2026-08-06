package org.jetbrains.sbt.project.directoryCompletion

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import org.jetbrains.plugins.scala.util.assertions.CollectionsAssertions.assertCollectionEquals
import org.jetbrains.sbt.actions.SbtDirectoryCompletionContributor

import scala.jdk.CollectionConverters.CollectionHasAsScala

final class SbtDirectoryCompletionFixture(project: Project) {

  // BSP has the same production contributor shape via ScalaDirectoryCompletionContributorBase.
  // Keep this fixture sbt-specific until BSP tests need the same assertions.
  private val contributor = new SbtDirectoryCompletionContributor

  def assertVariants(
    directory: VirtualFile,
    expectedVariants: Seq[ExpectedDirectoryCompletionVariant]
  ): Unit = {
    val psiDirectory = PsiManager.getInstance(project).findDirectory(directory)
    val directoryPath = directory.getPath

    val variants = contributor.getVariants(psiDirectory).asScala.toSeq
    val actualVariants = variants.map(v => ExpectedDirectoryCompletionVariant(
      v.getPath.stripPrefix(directoryPath).stripPrefix("/"),
      v.getRootType
    ))

    assertCollectionEquals(
      "Wrong directory completion contributor variants",
      expectedVariants.sorted,
      actualVariants.sorted
    )
  }

  def assertVariantsForProjectPaths(
    expectedSbtCompletionVariantsForParentModule: Seq[ExpectedDirectoryCompletionVariant],
    expectedSbtCompletionVariantsForMainModule: Seq[ExpectedDirectoryCompletionVariant],
    expectedSbtCompletionVariantsForTestModule: Seq[ExpectedDirectoryCompletionVariant],
    projectPaths: Seq[String],
    findVirtualFile: String => VirtualFile
  ): Unit =
    projectPaths.foreach { projectPath =>
      Seq(
        (projectPath, expectedSbtCompletionVariantsForParentModule),
        (s"$projectPath/src/main", expectedSbtCompletionVariantsForMainModule),
        (s"$projectPath/src/test", expectedSbtCompletionVariantsForTestModule)
      ).foreach { case (path, variants) =>
        assertVariants(findVirtualFile(path), variants)
      }
    }
}
