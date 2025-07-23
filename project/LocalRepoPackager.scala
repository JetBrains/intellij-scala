import coursier.cache.FileCache
import coursier.core.Dependency
import coursier.ivy.IvyRepository
import coursier.maven.{MavenRepository, SbtMavenRepository}
import coursier.{Fetch, Module, ModuleName, Organization, Repositories, moduleNameString, organizationString, util}
import sbt.*
import sbt.Keys.target
import sbt.librarymanagement.CrossVersion

import java.io.File
import java.net.URI
import java.nio.file.{Files, Path, Paths}
import scala.annotation.nowarn
import scala.util.matching.Regex

/**
 * Download artifacts from Maven and map them into a local repository, so that sbt can resolve artifacts locally without depending on online resolvers.
 */
object LocalRepoPackager extends AutoPlugin {

  val localRepoDependencies = settingKey[Seq[Dependency]]("dependencies to be downloaded into local repo")
  val localRepoUpdate = taskKey[Seq[(Path, Path)]]("create or update local repo")

  override def projectSettings: Seq[Def.Setting[?]] = Seq(
    localRepoUpdate := updateLocalRepo(
      localRepoDependencies.value,
      target.value.toPath
    )
  )

  /**
   * Create or update a local repository at `localRepoRoot` with given `dependencies`
   * and return the set of path it comprises.
   *
   * @return path mappings (file path -> local repo relative location)
   */
  def updateLocalRepo(dependencies: Seq[Dependency], targetDir: Path): Seq[(Path, Path)] = {

    @nowarn("cat=deprecation")
    val depsWithExclusions = dependencies
      .map(_.withExclusions(Set((org"org.scala-lang", name"scala-library"))))

    val fetch: Fetch[util.Task] = Fetch()
      .addRepositories(SbtMavenRepository(Repositories.central))
      .withDependencies(depsWithExclusions)
      .allArtifactTypes()
      .withMainArtifacts()
      .addExtraArtifacts { dpas =>
        // hack to get poms, signatures and checksums as "artifacts"
        dpas.flatMap { case (_, _, art) =>
          art.extra.get("metadata").toSeq
        }
      }

    val fetched = fetch.run().map(_.toPath)
    fetched.flatMap { src =>
      repositoryRoots(fetch, src).flatMap { root =>
        val relativePath = root.relativize(src)
        assert(!relativePath.toString.contains(".."), s"""Relative path must not contain special name ".." (parent folder): $relativePath (root: $root, file: $src)""")
        src match {
          case SbtPluginJar(name, version) =>
            // SCL-24119
            // We need to include sbt plugin jars using the modern artifact name style (which contains the Scala and sbt version in the name)
            // and the "legacy" name style (which only has the name of the plugin).
            // We simply include the same artifact with two different names, the jars are otherwise identical.
            val legacyFileName = createLegacyFileName(name, version, extension = "jar")
            val legacyFileRelativePath = relativePath.resolveSibling(legacyFileName)
            Seq(
              src -> legacyFileRelativePath,
              src -> relativePath
            )

          case SbtPluginPom(name, scalaSbtVersion, version) =>
            // SCL-24119
            // We need to include the sbt plugin poms using the modern artifact name style and the "legacy" name style.
            // We create a copy of the pom file, with a modified <artifactId> inside with the "legacy" artifact name.
            val legacyFileName = createLegacyFileName(name, version, extension = "pom")
            val legacyFileRelativePath = relativePath.resolveSibling(legacyFileName)

            val localRepoTmpDir = targetDir.resolve("local-repo-tmp-dir")
            if (!Files.exists(localRepoTmpDir)) {
              Files.createDirectories(localRepoTmpDir)
            }

            val legacyFileCopy = localRepoTmpDir.resolve(legacyFileRelativePath)
            if (!Files.exists(legacyFileCopy.getParent)) {
              Files.createDirectories(legacyFileCopy.getParent)
            }

            val pomContents = Files.readString(src)
            val originalArtifactId = s"<artifactId>$name$scalaSbtVersion</artifactId>"
            val replacementArtifactId = s"<artifactId>$name</artifactId>"
            val modifiedPom = pomContents.replace(originalArtifactId, replacementArtifactId)
            val options = {
              import java.nio.file.StandardOpenOption.*
              Array(WRITE, CREATE, TRUNCATE_EXISTING)
            }

            Files.writeString(legacyFileCopy, modifiedPom, options*)

            Seq(
              legacyFileCopy -> legacyFileRelativePath,
              src -> relativePath
            )

          case _ =>
            Seq(src -> relativePath)
        }
      }
    }
  }

  private def createLegacyFileName(name: String, version: String, extension: String): String =
    s"$name-$version.$extension"

  private val SbtPluginFileName: Regex = {
    val ScalaSbtVersionStrings = Seq("_2.10_0.13", "_2.12_1.0", "_2.12_1.3")
    val helper = s"(.*)(${ScalaSbtVersionStrings.mkString("|")})-(.*)"
    s"$helper\\.(jar|pom)".r
  }

  private object SbtPluginJar {
    def unapply(path: Path): Option[(String, String)] = {
      val fileName = path.getFileName.toString
      if (fileName.endsWith("-javadoc.jar") || fileName.endsWith("-sources.jar")) return None

      fileName match {
        case SbtPluginFileName(name, _, version, extension) if extension == "jar" =>
          Some((name, version))
        case _ => None
      }
    }
  }

  private object SbtPluginPom {
    def unapply(path: Path): Option[(String, String, String)] = {
      val fileName = path.getFileName.toString
      fileName match {
        case SbtPluginFileName(name, scalaSbtVersion, version, extension) if extension == "pom" =>
          Some((name, scalaSbtVersion, version))
        case _ => None
      }
    }
  }

  def relativeJarPath(dep: Dependency): Path = {
    val fetch = Fetch()
      .addRepositories(SbtMavenRepository(Repositories.central))
      .addDependencies(dep)
      .noExtraArtifacts()

    val artifact = fetch
      .runResult()
      .detailedArtifacts
      .find(_._1.moduleVersion == dep.moduleVersion)
      .map(_._4.toPath)

    val res: Seq[Path] = for {
      artifact <- artifact.toSeq
      root <- repositoryRoots(fetch, artifact)
    } yield root.relativize(artifact)
    res.head
  }

  // Q: this method logic looks very similar to sbt.Defaults.sbtPluginExtra. Can we somehow reuse it?
  def sbtDep(org: String, moduleName: String, version: String, sbtVersion: String): Dependency = {
    val scalaBinVer = scalaBinaryVersionForSbtVersion(sbtVersion)
    val module = sbtCrossModule(ModuleID(org, moduleName, version), sbtVersion, scalaBinVer)
    Dependency(module, version)
  }

  // NOTE: I couldn't find a similar utility method in sbt (at least in sbt 1.10.7)
  // There is sbt.PluginCross.scalaVersionFromSbtBinaryVersion, but it's not updated for sbt 2.0 and is private
  private def scalaBinaryVersionForSbtVersion(sbtVersion: String): String =
    if (sbtVersion == "0.13") "2.10"
    else if (sbtVersion.startsWith("1.")) "2.12"
    else if (sbtVersion.startsWith("2.")) "3"
    else throw new IllegalArgumentException(s"unsupported sbt version: $sbtVersion")

  private def sbtCrossModule(
    moduleId: ModuleID,
    sbtVersion: String,
    scalaVersion: String,
  ): coursier.Module = {
    val moduleName = moduleId.name
    val moduleIdNew = Defaults.sbtPluginExtra(moduleId, sbtVersion, scalaVersion)
    val moduleNameNew = CrossVersion(moduleIdNew.crossVersion, sbtVersion, scalaVersion).fold(moduleName)(_(moduleName))
    // stripping prefix like in `lmcoursier.FromSbt`
    val attributesForCoursier = moduleIdNew.extraAttributes.map { case (k, v) => k.stripPrefix("e:") -> v }
    Module(
      Organization(moduleId.organization),
      ModuleName(moduleNameNew),
      attributesForCoursier
    )
  }

  private def repositoryRoots(fetch: Fetch[coursier.util.Task], artifact: Path): Seq[Path] = {
    val cacheRoot = fetch.cache.asInstanceOf[FileCache[Any]].location.toPath

    //we check for ivy, mainly for artifacts published locally for testing purposes
    val isFromIvy = artifact.toString.contains(".ivy2")
    if (isFromIvy)
      fetch.repositories.collect {
        case repo: IvyRepository =>
          val rootStr = repo.pattern.chunks.collectFirst { case c if c.string.contains("file:/") => c.string }.getOrElse {
            throw new RuntimeException(s"Can't determine .ivy2 root for coursier repo $repo, for artifact $artifact")
          }
          val root = new URI(rootStr)
          new File(root).toPath
      }
    else
      fetch.repositories.collect {
        case repo: MavenRepository =>
          val root = new URI(repo.root)
          val relativeRepoRoot = Paths.get(root.getScheme, root.getSchemeSpecificPart)
          cacheRoot.resolve(relativeRepoRoot)
      }
  }

}
