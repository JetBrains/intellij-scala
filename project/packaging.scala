import sbt._
import Keys._

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

  def packagePlugin(fileMappings: Seq[(File, File)], destination: File): Unit = {
    IO.delete(destination)
    val (dirs, files) = fileMappings.partition(_._1.isDirectory)
    dirs  foreach { case (from, to) => IO.copyDirectory(from, to, overwrite = true) }
    files foreach { case (from, to) => IO.copyFile(from, to)}
  }

  def compressPackagedPlugin(source: File, destination: File): Unit =
    IO.zip((source.getParentFile ***) pair (relativeTo(source.getParentFile), false), destination)

  import PackageEntry._

  def convertEntriesToFileMappings(entries: Seq[PackageEntry], artifactPath: File, libraries: Classpath): Seq[(File, File)] = {
    val resolvedLibraries = (for {
      jarFile <- libraries
      moduleId <- jarFile.get(moduleID.key)
      key = (moduleId.organization % moduleId.name % moduleId.revision)
    } yield (key, jarFile.data)).toMap
    entries.map(e => convertEntry(e, artifactPath, resolvedLibraries))
  }

  private def convertEntry(entry: PackageEntry, base: File, resolvedLibraries: Map[ModuleID, File]): (File, File) =
    entry match {
      case Directory(source, destination) =>
        source -> base / destination
      case Artifact(source, destination) =>
        source -> base / destination
      case MergedArtifact(sources, destination) =>
        mergeIntoTemporaryJar(sources:_*) -> (base / destination)
      case Library(libraryId, destination) =>
        val libKey = libraryId.organization % libraryId.name % libraryId.revision
        resolvedLibraries(libKey) -> (base / destination)
    }

  private def mergeIntoTemporaryJar(filesToMerge: File*): File =
    IO.withTemporaryDirectory { tmp =>
      filesToMerge.foreach(IO.unzip(_, tmp))
      val zipFile = IO.temporaryDirectory / "sbt-merge-result.jar"
      zipFile.delete()
      IO.zip((tmp ***) pair (relativeTo(tmp), false), zipFile)
      zipFile
    }
}
