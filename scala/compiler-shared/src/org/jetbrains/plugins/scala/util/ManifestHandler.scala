package org.jetbrains.plugins.scala.util

import java.util.jar.{JarFile, Manifest}

import scala.util.Using

case class ManifestHandler(jarFile: java.io.File) {
  private[this] val separatorChar = java.io.File.separatorChar

  private val MANIFEST_ENTRY_NAME = s"META-INF/MANIFEST.MF"
  
  private val MAIN_CLASS_ENTRY_NAME = "Main-Class"
  private val CLASS_PATH_ENTRY_NAME = "Class-Path"

  private[this] val manifest = {
    Using.resource(new JarFile(jarFile)) { handler =>
      Option(handler.getEntry(MANIFEST_ENTRY_NAME)) map (e => new Manifest(handler.getInputStream(e)))
    }
  }

  def getMainClass: Option[String] = manifest flatMap (m => Option(m.getMainAttributes getValue MAIN_CLASS_ENTRY_NAME))

  def getClassPath: Option[Array[String]] =
    manifest flatMap (m => Option(m.getMainAttributes getValue CLASS_PATH_ENTRY_NAME)) map (_ split " ") map {
      pathEntry => pathEntry map (jarFile.getCanonicalPath + separatorChar + _.replace('/', separatorChar))
    }

  def getArbitraryAttribute(attrName: String): Option[String] = manifest flatMap (m => Option(m.getMainAttributes getValue attrName))

  def checkAttribute(name: String, value: String): Boolean =
    if (value == null) getArbitraryAttribute(name).isEmpty else getArbitraryAttribute(name) contains value
}
