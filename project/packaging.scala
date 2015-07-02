import sbt._
import Keys._

import CustomKeys._

object Packaging {

  sealed trait PackageEntry {
    val destination: String
  }

  object PackageEntry {
    final case class Directory(source: File, destination: String) extends PackageEntry
    final case class Artifact(source: File, destination: String) extends PackageEntry
    final case class MergedArtifact(sources: Seq[File], destination: String) extends PackageEntry
    final case class Library(source: ModuleID, destination: String) extends PackageEntry
  }

  lazy val packageSettings = Seq(
    packagePlugin <<= (dependencyClasspath.in(packagePlugin), packageStructure, baseDirectory.in(ThisBuild)).map {
      (libs, structure, baseDir) =>
        val pluginDir = baseDir / "out" / "plugin" / "Scala"
        new Packager(libs).pack(structure, pluginDir)
        pluginDir
    },
    packagePluginZip <<= (packagePlugin, baseDirectory.in(ThisBuild)).map { (pluginPath, baseDir) =>
      val zipFile = baseDir / "out" / "scala-plugin.zip"
      IO.zip((pluginPath.getParentFile ***) pair (relativeTo(pluginPath.getParentFile), false), zipFile)
      zipFile
    }
  )

  private class Packager(libraries: Classpath) {
    def pack(structure: Seq[PackageEntry], base: File): Unit = {
      val (dirs, files) = convertStructure(structure, base).partition(_._1.isDirectory)
      IO.delete(base)
      dirs  foreach { case (from, to) => IO.copyDirectory(from, to, overwrite = true) }
      files foreach { case (from, to) => IO.copyFile(from, to)}
    }

    import PackageEntry._

    private def convertStructure(entries: Seq[PackageEntry], base: File): Seq[(File, File)] =
      entries.map(e => convertEntry(e, base))

    private def convertEntry(entry: PackageEntry, base: File): (File, File) =
      entry match {
        case Directory(source, destination) =>
          source -> base / destination
        case Artifact(source, destination) =>
          source -> base / destination
        case MergedArtifact(sources, destination) =>
          mergeIntoOneJar(sources:_*) -> (base / destination)
        case Library(libraryId, destination) =>
          val libKey = libraryId.organization % libraryId.name % libraryId.revision
          resolvedLibraries(libKey) -> (base / destination)
      }

    private val resolvedLibraries = (for {
      jarFile <- libraries
      moduleId <- jarFile.get(moduleID.key)
      key = (moduleId.organization % moduleId.name % moduleId.revision)
    } yield (key, jarFile.data)).toMap

    private def mergeIntoOneJar(files: File*): File =
      IO.withTemporaryDirectory { tmp =>
        files.foreach(IO.unzip(_, tmp))
        val zipFile = IO.temporaryDirectory / "sbt-merge-result.jar"
        zipFile.delete()
        IO.zip((tmp ***) pair (relativeTo(tmp), false), zipFile)
        zipFile
      }
  }
}
