package org.jetbrains.plugins.scala.packagesearch.codeInspection

import com.intellij.codeInspection.{LocalInspectionTool, LocalQuickFix, ProblemsHolder}
import com.intellij.psi.PsiElement
import org.apache.maven.artifact.versioning.ComparableVersion
import org.jetbrains.plugins.scala.codeInspection.{PsiElementVisitorSimple, ScalaInspectionBundle}
import org.jetbrains.plugins.scala.extensions.SeqExt
import org.jetbrains.plugins.scala.packagesearch.codeInspection.DependencyVersionInspection.DependencyDescriptor
import org.jetbrains.plugins.scala.packagesearch.util.DependencyUtil

import scala.math.Ordered.orderingToOrdered

abstract class DependencyVersionInspection extends LocalInspectionTool {
  protected def isAvailable(element: PsiElement): Boolean

  protected def createDependencyDescriptor(element: PsiElement): Option[DependencyDescriptor]

  protected def createQuickFix(element: PsiElement, newerVersion: String): LocalQuickFix

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitorSimple = { element =>
    if (isAvailable(element)) {
      createDependencyDescriptor(element).foreach { dependencyDescriptor =>
        val versions = DependencyUtil.getDependencyVersions(dependencyDescriptor, element, onlyStable = true)

        val currentVersion = dependencyDescriptor.version
        val newestStableVersion = versions
          .sort(reverse = true)
          .headOption
          .collect {
            // don't suggest update from 3.2.1-RC1 to 3.2.0
            case newVersion if currentVersion.fold(true)(version => newVersion > new ComparableVersion(version)) =>
              newVersion.toString
          }

        newestStableVersion.foreach { newVersion =>
          // if new version is different or current version is empty
          if (!currentVersion.contains(newVersion)) {
            holder.registerProblem(
              element,
              ScalaInspectionBundle.message("packagesearch.newer.stable.version.available", dependencyDescriptor.groupId, dependencyDescriptor.artifactId),
              createQuickFix(element, newVersion)
            )
          }
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
  final case class DependencyDescriptor(groupId: String, artifactId: String, version: Option[String],
                                        artifactIdSuffix: ArtifactIdSuffix = ArtifactIdSuffix.Empty,
                                        platform: Boolean = false)
}
