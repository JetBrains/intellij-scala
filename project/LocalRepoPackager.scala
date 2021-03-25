import coursier.cache.FileCache
import coursier.core.Dependency
import coursier.maven.MavenRepository
import coursier.util.Artifact
import coursier.{Classifier, Fetch, Module, ModuleName, Organization, moduleNameString, organizationString}
import sbt.Keys.baseDirectory
import sbt._
import sbt.io.IO

import java.io.File
import java.net.URI
import java.nio.file.{Path, Paths}

/**
  * Download artifacts from Maven and map them into a local repository, so that sbt can resolve artifacts locally without depending on online resolvers.
  */
object LocalRepoPackager extends AutoPlugin {
  
  val localRepoDependencies = settingKey[Seq[Dependency]]("dependencies to be downloaded into local repo")
  val localRepoUpdate = taskKey[Seq[(Path,Path)]]("create or update local repo")

  override def projectSettings: Seq[Def.Setting[_]] = Seq(
    localRepoUpdate := updateLocalRepo(
      localRepoDependencies.value,
      (ThisBuild/baseDirectory).value.toPath / "project" / "resources")
  )

  /**
   * Create or update a local repository at `localRepoRoot` with given `dependencies`
   * and return the set of path it comprises.
   * @return path mappings (file path -> local repo relative location)
   */
  def updateLocalRepo(dependencies: Seq[Dependency], resourceDir: Path): Seq[(Path,Path)] = {
    val depsWithExclusions = dependencies
      .map(_.withExclusions(Set((org"org.scala-lang", name"scala-library"))))

    def artifactExtra(art: Artifact) = {
      val sig = art.extra.get("sig").toSeq
      val checksums = art.checksumUrls.values
        .map(url => Artifact(url, Map.empty, Map.empty, art.changing, optional = true, art.authentication))
      sig ++ checksums
    }

    val fetch = Fetch()
      .withDependencies(depsWithExclusions)
      .allArtifactTypes()
      .addClassifiers(Classifier.javadoc, Classifier.sources)
      .withMainArtifacts()
      .addExtraArtifacts { dpas =>
        // hack to get poms, signatures and checksums as "artifacts"
        dpas.flatMap { case (_,_,art) =>
          val meta = art.extra.get("metadata").toSeq
            .flatMap(a => List(a) ++ artifactExtra(a))
          meta ++ artifactExtra(art)
        }
      }

    val fetched = fetch.run().map(_.toPath)
    val srcTrg = for {
      src <- fetched
      root <- mavenRepoRoots(fetch)
    } yield (src, root.relativize(src))

    // replace javadocs with dummies because they are large and mostly useless, but some resolvers error out if they are missing
    val dummyJavadocJar = resourceDir / "dummy-javadoc.jar"
    val dummyJavadocMD5 = resourceDir / "dummy-javadoc.jar.md5"
    val res = srcTrg.flatMap { case (src, trg) =>
      val srcName = src.getFileName.toString
      if (srcName.endsWith("-javadoc.jar")) List(dummyJavadocJar -> trg)
      else if (srcName.endsWith("-javadoc.jar.md5")) List(dummyJavadocMD5 -> trg)
      else if (srcName.contains("-javadoc.jar")) List.empty // remove any other javadoc related files
      else List(src -> trg)
    }
    res
  }

  def relativeJarPath(dep: Dependency): Path = {
    val fetch = Fetch().addDependencies(dep).noExtraArtifacts()
    val roots = mavenRepoRoots(fetch)
    fetch
      .runResult()
      .detailedArtifacts
      .find(_._1.moduleVersion == dep.moduleVersion)
      .map(_._4.toPath)
      .flatMap(p => roots.map(_.relativize(p)).headOption)
      .head
  }

  def sbtDep(org: String, moduleName: String, version: String, sbtVersion: String): Dependency = {
    val scalaVersion = sbtVersion match {
      case "0.13" => "2.10"
      case "1.0" => "2.12"
      case _ => throw new IllegalArgumentException(s"unsupported sbt version: $sbtVersion")
    }

    val mod = Module(
      Organization(org), ModuleName(moduleName),
      attributes = Map("scalaVersion" -> scalaVersion, "sbtVersion" -> sbtVersion)
    )
    Dependency(mod, version)
  }

  private def mavenRepoRoots(fetch: Fetch[coursier.util.Task]): Seq[Path] = {
    val cacheRoot = fetch.cache.asInstanceOf[FileCache[Any]].location.toPath
    fetch.repositories.collect {
      case repo: MavenRepository =>
        val root = new URI(repo.root)
        val relativeRepoRoot = Paths.get(root.getScheme, root.getSchemeSpecificPart)
        cacheRoot.resolve(relativeRepoRoot)
    }
  }

}
