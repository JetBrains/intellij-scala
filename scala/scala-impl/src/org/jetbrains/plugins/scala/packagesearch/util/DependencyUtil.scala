package org.jetbrains.plugins.scala.packagesearch.util

import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.psi.PsiElement
import org.apache.maven.artifact.versioning.ComparableVersion
import org.jetbrains.packagesearch.api.v3.ApiMavenPackage
import org.jetbrains.plugins.scala.extensions.{IterableOnceExt, NonNullObjectExt, SeqExt}
import org.jetbrains.plugins.scala.packagesearch.api.PackageSearchClient
import org.jetbrains.plugins.scala.packagesearch.codeInspection.DependencyVersionInspection.{ArtifactIdSuffix, DependencyDescriptor}
import org.jetbrains.plugins.scala.project.{ProjectExt, ProjectPsiElementExt}

import scala.jdk.CollectionConverters.ListHasAsScala

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

  def getArtifacts(groupId: String, artifactId: String, useCache: Boolean, exactMatchGroupId: Boolean): List[ApiMavenPackage] = {
    val packagesFuture = PackageSearchClient.instance().searchByQuery(groupId, artifactId, useCache)
    val packages = ProgressIndicatorUtils.awaitWithCheckCanceled(packagesFuture)
      .asScala.toList
      .filterByType[ApiMavenPackage]
      .pipeIf(exactMatchGroupId)(_.filter(_.getGroupId == groupId))
    packages
  }

  def getArtifactVersions(groupId: String, artifactId: String, onlyStable: Boolean): Seq[ComparableVersion] = {
    val apiPackageFuture = PackageSearchClient.instance().searchById(groupId, artifactId)
    val apiPackage = ProgressIndicatorUtils.awaitWithCheckCanceled(apiPackageFuture)

    if (apiPackage == null) Seq.empty
    else apiPackage.getVersions
      .getAll.asScala.toSeq
      // NormalizedVersion.isStable is not applicable in some cases (SCL-22621), use DependencyUtil.isStable instead
      .map(_.getNormalizedVersion.getVersionName)
      .collect { case version if !onlyStable || isStable(version) =>
        // NormalizedVersion treats 1.0.0 and 1.0.0-RC1 as equal which is not a desired result
        new ComparableVersion(version)
      }
  }

  def getDependencyVersions(dependencyDescriptor: DependencyDescriptor, context: PsiElement, onlyStable: Boolean): Seq[ComparableVersion] = {
    val noScalaVersionSuffix = dependencyDescriptor.artifactIdSuffix == ArtifactIdSuffix.Empty

    // TODO(SCL-21495): handle platform specification
    if (noScalaVersionSuffix) getArtifactVersions(dependencyDescriptor.groupId, dependencyDescriptor.artifactId, onlyStable)
    else {
      val fullScalaVersionSuffix = dependencyDescriptor.artifactIdSuffix == ArtifactIdSuffix.FullScalaVersion
      val scalaVersions = DependencyUtil.getAllScalaVersionsOrDefault(context, majorOnly = !fullScalaVersionSuffix)
      scalaVersions.flatMap { scalaVersion =>
        val patchedArtifactId = DependencyUtil.buildScalaArtifactIdString(dependencyDescriptor.artifactId, scalaVersion, fullScalaVersionSuffix)
        getArtifactVersions(dependencyDescriptor.groupId, patchedArtifactId, onlyStable)
      }
    }
  }

  def getScala2CompilerVersions(onlyStable: Boolean): Seq[ComparableVersion] =
    getArtifactVersions(ScalaCompilerGroupId, Scala2CompilerArtifactId, onlyStable)

  def getScala3CompilerVersions(onlyStable: Boolean): Seq[ComparableVersion] =
    getArtifactVersions(ScalaCompilerGroupId, Scala3CompilerArtifactId, onlyStable)

  val ScalaCompilerGroupId = "org.scala-lang"
  val Scala2CompilerArtifactId = "scala-compiler"
  val Scala3CompilerArtifactId = "scala3-compiler_3"
}
