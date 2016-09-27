package org.jetbrains.plugins.scala.project.migration.handlers

import com.intellij.openapi.externalSystem.model.project.LibraryData
import com.intellij.openapi.roots.libraries.Library
import org.jetbrains.plugins.scala.project.migration.ScalaLibraryMigrator

/**
  * User: Dmitry.Naydanov
  * Date: 01.09.16.
  */
trait ScalaLibraryMigrationHandler {
  def acceptsFrom(from: Library): Boolean
  
  def acceptsTo(to: LibraryData): Boolean
  
  def getMigrators(from: Library, to: LibraryData): Iterable[ScalaLibraryMigrator]

  /**
    * Checks if the other handler can be substituted by this one.
    *
    * @param otherOne other handler
    * @return true if yes, false otherwise (regardless of other one supports another version(s) 
    *         of the same lib or just a different lib(s)
    */
  def precede(otherOne: ScalaLibraryMigrationHandler): Boolean
}
