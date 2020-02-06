package org.jetbrains.plugins.scala.project.migration.store

import java.io.File

import org.jetbrains.plugins.scala.project.migration.store.ManifestAttributes._
import org.jetbrains.plugins.scala.util.ManifestHandler

/**
  * User: Dmitry.Naydanov
  * Date: 29.09.16.
  */
object SerializationUtil {
  private val allAttributes = Seq(MigratorFqnAttribute, InspectionPackageAttribute)
  
  def discoverIn(filePath: String): Seq[(ManifestAttribute, String)] = {
    val path = stripPath(filePath)
    if (!path.endsWith(".jar")) return Seq.empty
    
    val ioFile = new File(path)
    if (!ioFile.exists()) return Seq.empty
    
    val manifest = ManifestHandler(ioFile)

    allAttributes.flatMap {
      attribute =>
        manifest.getArbitraryAttribute(attribute.name).toSeq.flatMap(v => attribute parse v).map(a => (attribute, a))
    }
  }
  
  def stripPath(filePath: String) = filePath.stripSuffix("/").stripSuffix("\\").stripSuffix("!")
}
