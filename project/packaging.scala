import sbt._
import Keys._


object Packaging {
  def packageStructure: Def.Initialize[Task[Seq[(File, String)]]] = Def.task {
    packageArtifacts.value ++ packageUnmanagedJars.value ++ packageLibraries.value
  }

  private def getArtifactPath(projectName: String) = Def.task {
    artifactPath.in(new LocalProject(projectName), Compile, packageBin).value
  }

  private def mergeIntoOneJar(files: File*): File =
    IO.withTemporaryDirectory { tmp =>
      files.foreach(IO.unzip(_, tmp))
      val zipFile = IO.temporaryDirectory / "sbt-merge-result.jar"
      zipFile.delete()
      IO.zip((tmp ***) pair (relativeTo(tmp), false), zipFile)
      zipFile
    }

  private def packageArtifacts = Def.task {
    Seq(
      getArtifactPath("scalaCommunity").value ->
        "lib/scala-plugin.jar",
      getArtifactPath("compilerSettings").value ->
        "lib/compiler-settings.jar",
      getArtifactPath("nailgunRunners").value ->
        "lib/scala-nailgun-runner.jar",
      getArtifactPath("jpsPlugin").value ->
        "lib/jps/scala-jps-plugin.jar",
      mergeIntoOneJar(getArtifactPath("runners").value, getArtifactPath("scalaRunner").value) ->
        "lib/scala-plugin-runners.jar"
    )
  }

  private def packageUnmanagedJars = Def.task {
    Seq(
      file("SDK/nailgun")     -> "lib/jps/",
      file("SDK/sbt")         -> "lib/jps/",
      file("SDK/scalap")      -> "lib/",
      file("SDK/scalastyle")  -> "lib/"
    )
  }

  private def getDependencyClasspath(projectName: String) = Def.task {
    dependencyClasspath.in(new LocalProject(projectName), Compile).value
  }

  private def packageLibraries = Def.task {
    val resolveClasspath =
      getDependencyClasspath("scalaCommunity").value ++
        getDependencyClasspath("runners").value ++
        getDependencyClasspath("sbtRuntimeDependencies").value
    val resolvedLibraries = (for {
      jarFile <- resolveClasspath
      moduleId <- jarFile.get(moduleID.key)
      key = (moduleId.organization % moduleId.name % moduleId.revision)
    } yield (key, jarFile.data)).toMap
    def libOf(lib: ModuleID, prefix: String = "lib/") = {
      val libKey = lib.organization % lib.name % lib.revision
      resolvedLibraries(libKey) -> (prefix + resolvedLibraries(libKey).name)
    }

    val renamedLibraries = Seq(
      libOf(Dependencies.sbtStructureExtractor012)._1 -> "launcher/sbt-structure-0.12.jar",
      libOf(Dependencies.sbtStructureExtractor013)._1 -> "launcher/sbt-structure-0.13.jar",
      libOf(Dependencies.sbtLaunch)._1 -> "launcher/sbt-launch.jar",
      libOf(Dependencies.scalaLibrary)._1 -> "lib/scala-library.jar"
    )

    val crossLibraries = Seq(Dependencies.scalaParserCombinators, Dependencies.scalaXml).map { lib =>
      libOf(lib.copy(name = lib.name + "_2.11"))
    }

    val otherLibraries = DependencyGroups.scalaCommunity.filterNot { lib =>
      Seq(Dependencies.scalaLibrary, Dependencies.scalaParserCombinators, Dependencies.scalaXml).contains(lib)
    }

    renamedLibraries ++ crossLibraries ++ otherLibraries.map(libOf(_))
  }
}