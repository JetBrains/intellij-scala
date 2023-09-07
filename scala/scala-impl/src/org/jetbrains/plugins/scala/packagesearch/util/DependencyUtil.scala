package org.jetbrains.plugins.scala.packagesearch.util

import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.psi.PsiElement
import org.apache.maven.artifact.versioning.ComparableVersion
import org.jetbrains.plugins.scala.extensions.{ObjectExt, SeqExt}
import org.jetbrains.plugins.scala.packagesearch.api.PackageSearchApiClient
import org.jetbrains.plugins.scala.packagesearch.codeInspection.DependencyVersionInspection.{ArtifactIdSuffix, DependencyDescriptor}
import org.jetbrains.plugins.scala.project.{ProjectExt, ProjectPsiElementExt}

object DependencyUtil {

  // heuristic similar to what coursier does
  def isStable(version: String): Boolean =
    !version.toLowerCase.endsWith("snapshot") &&
      !version.exists(_.isLetter) &&
      version
        .split(Array('.', '-'))
        .forall(_.lengthCompare(5) <= 0)

  /**
   * Append scala version suffix to `artifactId`. If `fullVersion = false`, then:
   *  - for Scala 3 use `artifactId_3`
   *  - for Scala 2 use `artifactId_2.x` (2.13, 2.12, etc.)
   */
  def buildScalaArtifactIdString(artifactId: String, scalaVersion: String, fullVersion: Boolean): String = {
    if (scalaVersion == null || scalaVersion.isEmpty) artifactId
    else {
      val paddedScalaVersion = scalaVersion.split('.').padTo(3, "0")
      val partsToTake =
        if (fullVersion) 3
        else if (paddedScalaVersion.head == "3") 1
        else 2
      val versionString = paddedScalaVersion.take(partsToTake).mkString(".")
      s"${artifactId}_$versionString"
    }
  }

  def getAllScalaVersionsOrDefault(element: PsiElement, majorOnly: Boolean = false): Seq[String] = {
    val projectVersions = element.getProject.allScalaVersions.sort(reverse = true)

    if (projectVersions.isEmpty) {
      val langLevel = element.scalaLanguageLevelOrDefault
      val version = langLevel.getVersion.pipeIf(!majorOnly)(_ + ".0")
      List(version)
    } else {
      val versionStrings =
        if (majorOnly) projectVersions.map(_.major)
        else projectVersions.map(_.minor)
      versionStrings.distinct
    }
  }

  def getDependencyVersions(dependencyDescriptor: DependencyDescriptor, context: PsiElement, onlyStable: Boolean): Seq[ComparableVersion] = {
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
        val scalaVersions = DependencyUtil.getAllScalaVersionsOrDefault(context, majorOnly = !fullScalaVersionSuffix)
        scalaVersions.flatMap { scalaVersion =>
          val patchedArtifactId = DependencyUtil.buildScalaArtifactIdString(dependencyDescriptor.artifactId, scalaVersion, fullScalaVersionSuffix)
          getVersions(patchedArtifactId)
        }
      }

    versions.collect {
      case version if !onlyStable || isStable(version) =>
        new ComparableVersion(version)
    }
  }
}
