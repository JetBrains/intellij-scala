package org.jetbrains.plugins.scala.packagesearch.util

import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.psi.PsiElement
import com.intellij.util.concurrency.AppExecutorUtil
import org.apache.maven.artifact.versioning.ComparableVersion
import org.jetbrains.annotations.{ApiStatus, VisibleForTesting}
import org.jetbrains.packagesearch.api.v3.ApiMavenPackage
import org.jetbrains.plugins.scala.extensions.{IterableOnceExt, NonNullObjectExt, SeqExt}
import org.jetbrains.plugins.scala.isUnitTestMode
import org.jetbrains.plugins.scala.packagesearch.api.PackageSearchClient
import org.jetbrains.plugins.scala.packagesearch.codeInspection.DependencyVersionInspection.{ArtifactIdSuffix, DependencyDescriptor}
import org.jetbrains.plugins.scala.project.{ProjectExt, ProjectPsiElementExt}

import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicReference
import scala.jdk.CollectionConverters.ListHasAsScala

object DependencyUtil {
  private[this] val versionCompletion = VersionCompletion.instance()

  @ApiStatus.Internal
  @VisibleForTesting
  private[jetbrains] def updateMockVersionCompletionCache(newCache: ((String, String), Seq[String])*): Unit =
    VersionCompletion.MockVersionCompletion.cache.set(Map(newCache: _*))

  private sealed trait VersionCompletion {
    def getVersions(groupId: String, artifactId: String): Seq[String]
  }

  private object VersionCompletion {
    private[DependencyUtil] def instance(): VersionCompletion =
      if (isUnitTestMode) MockVersionCompletion else CoursierVersionCompletion

    private[DependencyUtil] object MockVersionCompletion extends VersionCompletion {
      override def getVersions(groupId: String, artifactId: String): Seq[String] =
        cache.get().getOrElse(groupId -> artifactId, Seq.empty)

      val cache: AtomicReference[Map[(String, String), Seq[String]]] = new AtomicReference(Map.empty)
    }

    private object CoursierVersionCompletion extends VersionCompletion {
      private[this] val complete = coursierapi.Complete.create()

      override def getVersions(groupId: String, artifactId: String): Seq[String] = try {
        // Make the blocking call a bit more cancellable
        val resultFuture = CompletableFuture
          .supplyAsync(() => complete.withInput(s"$groupId:$artifactId:").complete(), AppExecutorUtil.getAppExecutorService)
        val result = ProgressIndicatorUtils.awaitWithCheckCanceled(resultFuture)
        result.getCompletions.asScala.toSeq
      } catch {
        case _: coursierapi.error.CoursierError => Seq.empty
      }
    }
  }

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
    val allVersions = versionCompletion.getVersions(groupId, artifactId)
    allVersions.collect { case version if !onlyStable || isStable(version) =>
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
