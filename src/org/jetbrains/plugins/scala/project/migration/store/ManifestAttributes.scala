package org.jetbrains.plugins.scala.project.migration.store

/**
  * User: Dmitry.Naydanov
  * Date: 29.09.16.
  */
object ManifestAttributes {
  trait ManifestAttribute {
    def name: String
  }
  
  object MigratorFqnAttribute extends ManifestAttribute {
    override def name: String = "Intellij-Migrator-Fqn"
  }
  
  object InspectionPackageAttribute extends ManifestAttribute {
    override def name: String = "Intellij-Inspection-Package"
  }
}
