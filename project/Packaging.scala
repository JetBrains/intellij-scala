import java.io.File

import sbt.Keys._
import sbt._

object Packaging {
  sealed trait PackageEntry {
    val destination: String
  }

  object PackageEntry {
    final case class Directory(source: File, destination: String) extends PackageEntry
    final case class Artifact(source: File, destination: String) extends PackageEntry
    final case class MergedArtifact(sources: Seq[File], destination: String) extends PackageEntry
    final case class Library(source: ModuleID, destination: String) extends PackageEntry
    final case class AllOrganisation(org: String, destination: String) extends PackageEntry
  }

  def packagePlugin(mappings: Seq[(File, String)], destination: File): Unit = {
    IO.delete(destination)
    val (dirs, files) = mappings.partition(_._1.isDirectory)
    dirs  foreach { case (from, to) => IO.copyDirectory(from, destination / to, overwrite = true) }
    files foreach { case (from, to) => IO.copyFile(from, destination / to)}
  }

  def compressPackagedPlugin(source: File, destination: File): Unit =
    IO.zip((source.getParentFile ***) pair (relativeTo(source.getParentFile), false), destination)

  import PackageEntry._

  def convertEntriesToMappings(entries: Seq[PackageEntry], libraries: Classpath): Seq[(File, String)] = {
    val resolvedLibraries = (for {
      jarFile <- libraries
      moduleId <- jarFile.get(moduleID.key)
      key = moduleId.organization % moduleId.name % moduleId.revision
    } yield (key, jarFile.data)).toMap
    entries.map(e => convertEntry(e, resolvedLibraries))
  }

  def pluginVersion: String =
    Option(System.getProperty("plugin.version")).getOrElse("SNAPSHOT")

  def replaceInFile(f: File, source: String, target: String): Unit = {
    if (!(source == null) && !(target == null)) {
      IO.writeLines(f, IO.readLines(f) map { _.replace(source, target) })
    }
  }

  def patchedPluginXML(mapping: (File, String)): (File, String) = {
    val (f, path) = mapping
    val tmpFile = java.io.File.createTempFile("plugin", ".xml")
    IO.copyFile(f, tmpFile)
    replaceInFile(tmpFile, "VERSION", pluginVersion)
    (tmpFile, path)
  }

  private def convertEntry(entry: PackageEntry, resolvedLibraries: Map[ModuleID, File]): (File, String) = {
    entry match {
      case Directory(source, destination) =>
        source -> destination
      case Artifact(source, destination) =>
        source -> destination
      case MergedArtifact(srcs, destination) =>
        mergeIntoTemporaryJar(srcs: _*) -> destination
      case Library(libraryId, destination) =>
        val libKey = libraryId.organization % libraryId.name % libraryId.revision
        resolvedLibraries(libKey) -> destination
      case AllOrganisation(org, destination) =>
        mergeIntoTemporaryJar(resolvedLibraries.filter(_._1.organization == org).values.toSeq: _*) -> destination
    }
  }

  private def mergeIntoTemporaryJar(filesToMerge: File*): File =
    IO.withTemporaryDirectory { tmp =>
      filesToMerge.foreach(IO.unzip(_, tmp))
      val zipFile =  File.createTempFile("sbt-merge-result",".jar", IO.temporaryDirectory)
      zipFile.delete()
      IO.zip((tmp ***) pair (relativeTo(tmp), false), zipFile)
      zipFile
    }
}
