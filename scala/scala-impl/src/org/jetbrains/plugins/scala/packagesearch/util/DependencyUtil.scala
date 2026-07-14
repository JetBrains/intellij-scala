// TODO: move to a more appropriate package
package org.jetbrains.plugins.scala.packagesearch.util

import com.intellij.openapi.diagnostic.{ControlFlowException, Logger}
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.psi.PsiElement
import com.intellij.util.concurrency.AppExecutorUtil
import org.apache.maven.artifact.versioning.ComparableVersion
import org.jetbrains.annotations.{ApiStatus, VisibleForTesting}
import org.jetbrains.plugins.scala.extensions.{NonNullObjectExt, SeqExt}
import org.jetbrains.plugins.scala.packagesearch.codeInspection.DependencyVersionInspection.{ArtifactIdSuffix, DependencyDescriptor}
import org.jetbrains.plugins.scala.packagesearch.lang.completion.BaseDependencyCompletionParameters
import org.jetbrains.plugins.scala.project.{ProjectExt, ProjectPsiElementExt}
import org.jetbrains.plugins.scala.{LatestScalaVersions, isUnitTestMode}

import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicReference
import scala.jdk.CollectionConverters.ListHasAsScala
import scala.util.control.NonFatal
import scala.util.matching.Regex

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
      import coursierapi.Complete

      private val Log: Logger = Logger.getInstance(classOf[CoursierVersionCompletion.type])

      private[this] val completeApiFuture: CompletableFuture[Complete] = {
        Log.info("Asynchronously instantiating the coursier completion API in a background thread")
        CompletableFuture.supplyAsync(
          () => {
            val api = Complete.create()
            Log.info("Finished initialising the coursier completion API")
            api
          },
          AppExecutorUtil.getAppExecutorService
        )
      }

      override def getVersions(groupId: String, artifactId: String): Seq[String] = try {
        // Make the blocking call a bit more cancellable
        val resultFuture = completeApiFuture.thenApplyAsync(
          (c: Complete) => {
            val input = s"$groupId:$artifactId:"
            val result = c.withInput(input).complete()
            Log.debug(s"Coursier completion result for '$input': $result")
            result
          },
          AppExecutorUtil.getAppExecutorService
        )
        val result = ProgressIndicatorUtils.awaitWithCheckCanceled(resultFuture)
        result.getCompletions.asScala.toSeq
      } catch {
        case e: ControlFlowException => throw e
        case NonFatal(_) => Seq.empty
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

  /**
   * Returns `true` unless the given `artifactId` is a Scala cross-published artifact whose Scala version is
   * incompatible with the Scala version(s) used in the project.
   *
   * Only suffixes that actually look like a Scala version are considered: a known binary version (`_3`, `_2.13`)
   * or a full version under a known binary version (`_2.13.5`, `_3.3.0`). Regular artifacts that merely end in
   * `_<number>` (e.g. `commons-lang_2`) are not treated as cross-published and are never filtered out.
   *
   * e.g.: `foo:bar_2.13` is compatible with Scala 2.13.2, `foo:bar_3` is compatible with Scala 3.8.0, etc.
   */
  def isArtifactCompatible(params: BaseDependencyCompletionParameters[_ <: PsiElement], artifactId: String): Boolean = artifactId match {
    case CrossPublishedArtifact(_, scalaVersionSuffix) if isScalaVersionSuffix(scalaVersionSuffix) =>
      params.scalaVersions.exists(isCompatibleScalaVersion(_, scalaVersionSuffix))
    case _ => true
  }

  /** `true` if `suffix` is a known Scala binary version (e.g., `2.13`, `3`) or a full version under one (e.g., `2.13.5`). */
  private def isScalaVersionSuffix(suffix: String): Boolean =
    ScalaMajorVersions.exists(binaryVersion => suffix == binaryVersion || suffix.startsWith(s"$binaryVersion."))

  /** `true` if `projectScalaVersion` (a full version, e.g. `2.13.5`) matches the cross-published `suffix`. */
  private def isCompatibleScalaVersion(projectScalaVersion: String, suffix: String): Boolean =
    projectScalaVersion == suffix || projectScalaVersion.startsWith(s"$suffix.")

  def getScala2CompilerVersions(onlyStable: Boolean): Seq[ComparableVersion] =
    getArtifactVersions(ScalaCompilerGroupId, Scala2CompilerArtifactId, onlyStable)

  def getScala3CompilerVersions(onlyStable: Boolean): Seq[ComparableVersion] =
    getArtifactVersions(ScalaCompilerGroupId, Scala3CompilerArtifactId, onlyStable)

  val ScalaCompilerGroupId = "org.scala-lang"
  val Scala2CompilerArtifactId = "scala-compiler"
  val Scala3CompilerArtifactId = "scala3-compiler_3"

  private val Scala2MajorVersions: Seq[String] = LatestScalaVersions.allScala2.map(_.major)
  private val ScalaMajorVersions: Seq[String] = Scala2MajorVersions :+ "3"
  private val CrossPublishedArtifact: Regex = "^(.+)_(\\d+.*)$".r

  /**
   * Splits a Maven `artifactId` into the base artifact name and its Scala cross-publishing suffix kind.
   * Callers can then render the result in their own DSL (sbt `%`/`%%`, scala-directive `:`/`::`/`:::`).
   *
   * Examples:
   * `cats-core_3` / `cats-core_2.13` -> `("cats-core", ScalaVersion)`
   * `some-macro_2.13.5` -> `("some-macro", FullScalaVersion)`
   * `commons-lang3` -> `("commons-lang3", Empty)`
   */
  def splitScalaArtifactIdSuffix(artifactId: String): (String, ArtifactIdSuffix) = artifactId match {
    case CrossPublishedArtifact(baseArtifactId, version) =>
      val kind = if (ScalaMajorVersions.contains(version)) ArtifactIdSuffix.ScalaVersion else ArtifactIdSuffix.FullScalaVersion
      (baseArtifactId, kind)
    case _ => (artifactId, ArtifactIdSuffix.Empty)
  }
}
