import java.io._
import java.util.zip.{ZipException, ZipInputStream, ZipOutputStream}

import sbt.Keys._
import sbt._

object Packaging {

  case class ModuleKey(id:ModuleID, attributes: Map[String,String])

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
    val (dirs, files) = mappings.partition(_._1.isDirectory)
    val toRemove = destination.***.get.toSet -- files.map(_._1)
    IO.delete(toRemove)

    dirs.foreach { case (from, to) =>
      IO.copyDirectory(from, destination / to, overwrite = false, preserveLastModified = true) }
    files.foreach { case (from, to) =>
      IO.copyFile(from, destination / to, preserveLastModified = true)}
  }

  def putInTempJar(file: File): File = {
    val zipFile = File.createTempFile("sbt-another-one-temp-jar", ".jar", IO.temporaryDirectory)
    IO.zip(Seq((file, file.getName)), zipFile)
    zipFile
  }

  def compressPackagedPlugin(source: File, destination: File): Unit =
    IO.zip((source.getParentFile ***) pair (relativeTo(source.getParentFile), false), destination)

  import PackageEntry._

  def convertEntriesToMappings(entries: Seq[PackageEntry],
                               libraries: Classpath
                              ): Seq[(File, String)] = {
    val resolvedLibraries = (for {
      jarFile <- libraries
      moduleId <- jarFile.get(moduleID.key)
    } yield {
      (moduleKey(moduleId), jarFile.data)
    }).toMap
    entries.map(e => convertEntry(e, resolvedLibraries))
  }

  /**
    * Extract only key-relevant parts of the ModuleId, so that mappings succeed even if they contain extra attributes
    */
  def moduleKey(moduleId: ModuleID): ModuleKey =
    ModuleKey (
      moduleId.organization % moduleId.name % moduleId.revision,
      moduleId.extraAttributes
        .map { case (k,v) => k.stripPrefix("e:") -> v}
        .filter { case (k,_) => k == "scalaVersion" || k == "sbtVersion" }
    )

  def crossName(moduleId: ModuleID, scalaVersion: String): String = {
    import CrossVersion._
    val name = moduleId.name
    moduleId.crossVersion match {
      case Disabled => name
      case f: Full => name + "_" + f.remapVersion(scalaVersion)
      case b: Binary => name + "_" + b.remapVersion(Versions.Scala.binaryVersion(scalaVersion))
    }
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

  private def convertEntry(entry: PackageEntry,
                           resolvedLibraries: Map[ModuleKey, File]): (File, String) = {
    entry match {
      case Directory(source, destination) =>
        source -> destination
      case Artifact(source, destination) =>
        source -> destination
      case MergedArtifact(srcs, destination) =>
        mergeIntoTemporaryJar(srcs: _*) -> destination
      case Library(libraryId, destination) =>
        resolvedLibraries(moduleKey(libraryId)) -> destination
      case AllOrganisation(org, destination) =>
        mergeIntoTemporaryJar(
          resolvedLibraries.filter {
            case (key,file) => key.id.organization == org
          }.values.toSeq: _*) -> destination
    }
  }

  private def mergeIntoTemporaryJar(filesToMerge: File*): File = {
      val zipFile =  File.createTempFile("sbt-merge-result",".jar", IO.temporaryDirectory)
      fastMerge(filesToMerge, zipFile)
      zipFile
    }

  def copyZipContent(input: File, outStream: ZipOutputStream): Unit = {
    val inStream = new ZipInputStream(new BufferedInputStream(new FileInputStream(input)))
    try {
      val buffer = new Array[Byte](64 * 1024)
      var entry = inStream.getNextEntry
      while (entry != null) {
        try {
          outStream.putNextEntry(entry)
          var numRead = inStream.read(buffer)
          while (numRead > 0) {
            outStream.write(buffer, 0, numRead)
            numRead = inStream.read(buffer)
          }
          outStream.closeEntry()
        } catch {
          case ze: ZipException if ze.getMessage.startsWith("duplicate entry") => //ignore
          case e: IOException => println(s"$e")
        }
        entry = inStream.getNextEntry
      }
    } finally { if (inStream != null) inStream.close() }
  }

  def fastMerge(input: Seq[File], output: File): Unit = {
    val outStream = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(output)))
    try {
      for (file <- input) {
        copyZipContent(file, outStream)
      }
    } finally { if (outStream != null) outStream.close() }
  }

}
