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

import java.util
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicReference
import scala.jdk.CollectionConverters.{ListHasAsScala, SeqHasAsJava}
import scala.util.control.NonFatal
import scala.util.matching.Regex

/**
 * An interface to get Maven dependency coordinate completion.
 * It can answer questions, such as:
 * - What artifacts are published under a given Maven groupId, for example, `org.scala-lang`?
 * - For a given Maven groupId and artifactId, which version(s) of the artifact are available?
 *
 * The completion is "prefix-based", i.e., to get available artifact id, one needs to provide a full
 * groupId and (optional) artifactId prefix. Fuzzy search is not supported.
 *
 * The results might be incomplete if called under progress in a cancellable section.
 *
 * @see [[coursierapi]]
 * @see [[https://get-coursier.io/docs/cli-complete Coursier Complete]]
 * @see [[com.intellij.openapi.progress.ProgressIndicator]]
 * @see [[com.intellij.openapi.progress.util.ProgressIndicatorUtils#awaitWithCheckCanceled(java.util.concurrent.Future)]]
 */
sealed trait DependencyCompletion {
  /**
   * Get available Maven artifact group ids that start with a given prefix. If the prefix is empty,
   * return all available group ids.
   */
  def getGroupIds(groupIdPrefix: String): util.List[String]

  /**
   * Same as [[getGroupIds]] but only uses the local Ivy cache.
   */
  def getLocalGroupIds(groupIdPrefix: String): util.List[String]

  /**
   * Get available Maven artifact ids that start with a given prefix. If the prefix is empty,
   * return all available artifact ids for a given group id.
   */
  def getArtifactIds(groupId: String, artifactIdPrefix: String): util.List[String]

  /**
   * Get available Maven artifact versions that start with a given prefix. If the prefix is empty,
   * return all available artifact versions for a given group and artifact id.
   */
  def getVersions(groupId: String, artifactId: String, versionPrefix: String): util.List[String]
}

object DependencyCompletion {
  def instance(): DependencyCompletion =
    if (isUnitTestMode) MockDependencyCompletion
    else CoursierDependencyCompletion

  /**
   * A mock implementation of [[DependencyCompletion]] that is used in tests.
   */
  private[util] object MockDependencyCompletion extends DependencyCompletion {
    override def getGroupIds(groupIdPrefix: String): util.List[String] = {
      val groupIds = groupIdsCache.get()
      val filtered = filterByPrefix(groupIdPrefix, groupIds)
      filtered.asJava
    }

    override def getLocalGroupIds(groupIdPrefix: String): util.List[String] =
      getGroupIds(groupIdPrefix)

    override def getArtifactIds(groupId: String, artifactIdPrefix: String): util.List[String] = {
      val artifactIds = artifactIdsCache.get().getOrElse(groupId, Seq.empty)
      val filtered = filterByPrefix(artifactIdPrefix, artifactIds)
      filtered.asJava
    }

    override def getVersions(groupId: String, artifactId: String, versionPrefix: String): util.List[String] = {
      val versions = versionsCache.get().getOrElse(groupId -> artifactId, Seq.empty)
      val filtered = filterByPrefix(versionPrefix, versions)
      filtered.asJava
    }

    // filtering by prefix is essential to emulate coursier's behavior
    private def filterByPrefix(prefix: String, values: Seq[String]): Seq[String] =
      if (prefix.isEmpty || values.isEmpty) values
      else values.filter(_.toLowerCase.startsWith(prefix.toLowerCase))

    val groupIdsCache: AtomicReference[Seq[String]] = new AtomicReference(Seq.empty)
    val artifactIdsCache: AtomicReference[Map[String, Seq[String]]] = new AtomicReference(Map.empty)
    val versionsCache: AtomicReference[Map[(String, String), Seq[String]]] = new AtomicReference(Map.empty)
  }

  /**
   * A real implementation of [[DependencyCompletion]] that uses [[coursierapi]] to get the completions.
   */
  private object CoursierDependencyCompletion extends DependencyCompletion {

    import coursierapi.{Complete, IvyRepository}

    private val Log: Logger = Logger.getInstance(classOf[CoursierDependencyCompletion.type])

    private def createAsync[T](debugName: String)(init: () => T): CompletableFuture[T] = {
      Log.info(s"Asynchronously instantiating the $debugName in a background thread")
      CompletableFuture.supplyAsync(
        () => {
          val result = init()
          Log.info(s"Finished initialising the $debugName")
          result
        },
        AppExecutorUtil.getAppExecutorService
      )
    }

    private val completeApiFuture: CompletableFuture[Complete] = createAsync("coursier completion API") { () =>
      Complete.create()
    }

    private val completeApiLocalFuture: CompletableFuture[Complete] = createAsync("coursier local completion API") { () =>
      // copied and slightly modified from https://github.com/coursier/coursier/blob/9419643421d17b15842aa3ed324dae725fe44a9c/modules/coursier/jvm/src/main/scala/coursier/LocalRepositories.scala#L12
      val ivy2HomeUri = {
        val path =
          sys.props.get("coursier.ivy.home")
            .orElse(sys.props.get("ivy.home"))
            .getOrElse(sys.props("user.home") + "/.ivy2/")

        // a bit touchy on Windows... - don't try to manually write down the URI with s"file://..."
        // TODO: try to make eel-aware
        val str = new java.io.File(path).toURI.toString
        if (str.endsWith("/"))
          str
        else
          str + "/"
      }

      // copied and slightly modified from https://github.com/coursier/coursier/blob/9419643421d17b15842aa3ed324dae725fe44a9c/modules/coursier/jvm/src/main/scala/coursier/LocalRepositories.scala#L54
      val ivy2Cache = IvyRepository.of(
        ivy2HomeUri + "cache/" +
          "(scala_[scalaVersion]/)(sbt_[sbtVersion]/)[organisation]/[module]/[type]s/[artifact]-[revision](-[classifier]).[ext]",
        ivy2HomeUri + "cache/" +
          "(scala_[scalaVersion]/)(sbt_[sbtVersion]/)[organisation]/[module]/[type]-[revision](-[classifier]).[ext]",
      ).withDropInfoAttributes(true)

      Complete.create().withRepositories(ivy2Cache)
    }

    override def getGroupIds(groupIdPrefix: String): util.List[String] =
      doComplete(groupIdPrefix)

    override def getLocalGroupIds(groupIdPrefix: String): util.List[String] =
      doComplete(groupIdPrefix, completeApiLocalFuture)

    override def getArtifactIds(groupId: String, artifactIdPrefix: String): util.List[String] =
      doComplete(s"$groupId:$artifactIdPrefix")

    override def getVersions(groupId: String, artifactId: String, versionPrefix: String): util.List[String] =
      doComplete(s"$groupId:$artifactId:$versionPrefix")

    private def doComplete(input: String, completeApi: CompletableFuture[Complete] = completeApiFuture): util.List[String] = try {
      // Make the blocking call a bit more cancellable
      val future = completeApi.thenApplyAsync(
        (complete: Complete) => {
          val result = complete.withInput(input).complete()
          Log.debug(s"Coursier completion result for '$input': $result")
          val completions = result.getCompletions
          completions
        },
        AppExecutorUtil.getAppExecutorService
      )
      ProgressIndicatorUtils.awaitWithCheckCanceled(future)
    } catch {
      case e: ControlFlowException => throw e
      case NonFatal(_) => util.Collections.emptyList()
    }
  }
}

object DependencyUtil {
  private[this] val dependencyCompletion = DependencyCompletion.instance()

  @ApiStatus.Internal
  @VisibleForTesting
  private[jetbrains] def updateMockGroupIdCompletionCache(newCache: String*): Unit =
    DependencyCompletion.MockDependencyCompletion.groupIdsCache.set(newCache)

  @ApiStatus.Internal
  @VisibleForTesting
  private[jetbrains] def updateMockArtifactIdCompletionCache(newCache: (String, Seq[String])*): Unit =
    DependencyCompletion.MockDependencyCompletion.artifactIdsCache.set(newCache.toMap)

  @ApiStatus.Internal
  @VisibleForTesting
  private[jetbrains] def updateMockVersionCompletionCache(newCache: ((String, String), Seq[String])*): Unit =
    DependencyCompletion.MockDependencyCompletion.versionsCache.set(newCache.toMap)

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
    val allVersions = dependencyCompletion.getVersions(groupId, artifactId, versionPrefix = "").asScala.toSeq
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
