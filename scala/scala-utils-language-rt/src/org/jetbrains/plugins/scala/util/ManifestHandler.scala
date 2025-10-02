package org.jetbrains.plugins.scala.util

import java.nio.file.Path

case class ManifestHandler(jarFile: Path) {
  private final val MainClassEntryName = "Main-Class"
  private final val ClassPathEntryName = "Class-Path"

  private val manifest = JarManifestUtils.readManifest(jarFile)

  def getMainClass: Option[String] = getArbitraryAttribute(MainClassEntryName)

  def getClassPath: Option[Array[String]] =
    getArbitraryAttribute(ClassPathEntryName).map(_.split(" ")).map { pathEntry =>
      pathEntry.map { entry =>
        val jarFileRealPath = jarFile.toAbsolutePath.normalize()
        val entryReplaced = entry.replace('/', java.io.File.separatorChar)
        jarFileRealPath.resolve(entryReplaced).toString
      }
    }

  def getArbitraryAttribute(attrName: String): Option[String] = Option(manifest.getMainAttributes.getValue(attrName))

  def checkAttribute(name: String, value: String): Boolean =
    if (value == null) getArbitraryAttribute(name).isEmpty else getArbitraryAttribute(name).contains(value)
}
