package org.jetbrains.plugins.scala.packagesearch.codeInspection

import com.intellij.codeInspection.{LocalInspectionTool, LocalQuickFix, ProblemsHolder}
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.psi.PsiElement
import org.apache.maven.artifact.versioning.ComparableVersion
import org.jetbrains.plugins.scala.codeInspection.{PsiElementVisitorSimple, ScalaInspectionBundle}
import org.jetbrains.plugins.scala.extensions.SeqExt
import org.jetbrains.plugins.scala.packagesearch.api.PackageSearchApiClient
import org.jetbrains.plugins.scala.packagesearch.codeInspection.DependencyVersionInspection.{ArtifactIdSuffix, DependencyDescriptor}
import org.jetbrains.plugins.scala.packagesearch.util.DependencyUtil

abstract class DependencyVersionInspection extends LocalInspectionTool {
  protected def isAvailable(element: PsiElement): Boolean

  protected def createDependencyDescriptor(element: PsiElement): Option[DependencyDescriptor]

  protected def createQuickFix(element: PsiElement, newerVersion: String): LocalQuickFix

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitorSimple = { element =>
    if (isAvailable(element)) {
      createDependencyDescriptor(element).foreach { dependencyDescriptor =>
        val noScalaVersionSuffix = dependencyDescriptor.artifactIdSuffix == ArtifactIdSuffix.Empty

        // TODO(SCL-21495): handle platform specification
        def getVersions(artifactId: String = dependencyDescriptor.artifactId): Seq[String] = {
          val versionFuture = PackageSearchApiClient.searchById(dependencyDescriptor.groupId, artifactId)
          val version = ProgressIndicatorUtils.awaitWithCheckCanceled(versionFuture)
          version.toList
            .flatMap(_.versions)
            .distinct
        }

        val versions =
          if (noScalaVersionSuffix) getVersions()
          else {
            val fullScalaVersionSuffix = dependencyDescriptor.artifactIdSuffix == ArtifactIdSuffix.FullScalaVersion
            val scalaVersions = DependencyUtil.getAllScalaVersionsOrDefault(element, majorOnly = !fullScalaVersionSuffix)
            scalaVersions.flatMap { scalaVersion =>
              val patchedArtifactId = DependencyUtil.buildScalaArtifactIdString(dependencyDescriptor.artifactId, scalaVersion, fullScalaVersionSuffix)
              getVersions(patchedArtifactId)
            }
          }

        val newestStableVersion = versions
          .collect { case v if DependencyUtil.isStable(v) => new ComparableVersion(v) }
          .sort(reverse = true)
          .headOption
          .fold(dependencyDescriptor.version)(_.toString)

        if (newestStableVersion != dependencyDescriptor.version) {
          holder.registerProblem(
            element,
            ScalaInspectionBundle.message("packagesearch.newer.stable.version.available", dependencyDescriptor.groupId, dependencyDescriptor.artifactId),
            createQuickFix(element, newestStableVersion)
          )
        }
      }
    }
  }
}

object DependencyVersionInspection {

  /**
   * Represents some addition to the artifactId when searching for dependency.
   *
   * E.g.: in sbt, dependency `"org" %% "artifact" % "version"` is resolved as `"org:artifact_scalaVersion:version"`
   */
  sealed trait ArtifactIdSuffix
  object ArtifactIdSuffix {
    case object Empty extends ArtifactIdSuffix
    case object ScalaVersion extends ArtifactIdSuffix
    case object FullScalaVersion extends ArtifactIdSuffix
  }

  /**
   * Package dependency descriptor
   *
   * @param platform whether the platform should be specified before `artifactIdSuffix` (e.g.: `sjs1` in `laminar_sjs1_3`) - probably only useful in Scala Directives
   */
  final case class DependencyDescriptor(groupId: String, artifactId: String, version: String,
                                        artifactIdSuffix: ArtifactIdSuffix = ArtifactIdSuffix.Empty,
                                        platform: Boolean = false)
}
