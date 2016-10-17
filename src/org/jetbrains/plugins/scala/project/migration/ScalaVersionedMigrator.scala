package org.jetbrains.plugins.scala.project.migration

import org.jetbrains.plugins.scala.project.Version

/**
  * User: Dmitry.Naydanov
  * Date: 06.09.16.
  */
trait ScalaVersionedMigrator extends ScalaLibraryMigrator { 
  def from: Iterable[Version]
  def to: Iterable[Version]
}
