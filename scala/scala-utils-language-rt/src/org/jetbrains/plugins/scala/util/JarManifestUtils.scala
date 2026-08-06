package org.jetbrains.plugins.scala.util

import org.jetbrains.annotations.ApiStatus

import java.io.BufferedInputStream
import java.nio.file.{FileSystems, Files, Path}
import scala.util.Using

object JarManifestUtils {

  def readManifest(jar: Path): java.util.jar.Manifest =
    Using.resource(FileSystems.newFileSystem(jar, null: ClassLoader)): fileSystem =>
      val manifestPath = fileSystem.getPath("META-INF", "MANIFEST.MF")
      Using.resource(BufferedInputStream(Files.newInputStream(manifestPath)))(java.util.jar.Manifest(_))

  def readManifestAttribute(jar: Path, attributeName: String): Option[String] =
    Option(readManifest(jar).getMainAttributes.getValue(attributeName))

  /**
   * @return Some list of classpath files if it's specified in the manifest. No validation is done for the files<br>
   *         None - if manifest or the class path attribute were not found
   */
  def readClassPath(jarFile: Path): Option[Seq[Path]] =
    readManifestAttribute(jarFile, "Class-Path").map: classPathAttribute =>
      val paths = classPathAttribute.split(" ").map(_.trim)
      val parentDirectory = jarFile.getParent
      paths.map(parentDirectory.resolve).map(_.toAbsolutePath.normalize()).toSeq

  /**
   * @return Some list of classpath files if it's specified in the manifest. No validation is done for the files<br>
   *         None - if manifest or the class path attribute were not found
   */
  @deprecated(message = "Use readClaspath(java.nio.file.Path)", since = "2026.2")
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2026.3")
  //noinspection SSBasedInspection
  def readClassPath(jarFile: java.io.File): Option[Seq[java.io.File]] =
    readClassPath(jarFile.toPath).map(_.map(_.toFile))
}
