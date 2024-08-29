package org.jetbrains.plugins.scala.util

import java.io.{BufferedInputStream, File}
import java.util.jar.JarFile

object JarManifestUtils {

  def readManifestAttributeFrom(file: File, name: String): Option[String] = {
    val jar = new JarFile(file)
    try {
      Option(jar.getJarEntry("META-INF/MANIFEST.MF")).flatMap { entry =>
        val input = new BufferedInputStream(jar.getInputStream(entry))
        val manifest = new java.util.jar.Manifest(input)
        val attributes = manifest.getMainAttributes
        Option(attributes.getValue(name))
      }
    } finally {
      jar.close()
    }
  }

  /**
   * @return Some list of classpath files if it's specified in the manifest. No validation is done for the files<br>
   *         None - if manifest or the class path attribute were not found
   */
  def readClassPath(jarFile: File): Option[Seq[File]] = {
    val classpathAttributeOpt = JarManifestUtils.readManifestAttributeFrom(jarFile, "Class-Path")
    classpathAttributeOpt.map { classPathAttribute =>
      val paths = classPathAttribute.split(" ").map(_.trim)
      val parentDirectory = jarFile.getParentFile
      paths.map(new File(parentDirectory, _)).map(_.getCanonicalFile).toSeq
    }
  }
}
