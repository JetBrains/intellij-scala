package org.jetbrains.plugins.scala.project.migration.handlers

import com.intellij.openapi.externalSystem.model.project.LibraryData
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.libraries.Library
import org.jetbrains.plugins.scala.project.migration.defaultimpl.ScalaStdLibHandler

/**
  * User: Dmitry.Naydanov
  * Date: 02.09.16.
  */
class ArtifactHandlerService(project: Project) {
  private val PREDEFINED_HANDLERS = Seq[ScalaLibraryMigrationHandler](new ScalaStdLibHandler)

  def getAll: Iterable[ScalaLibraryMigrationHandler] = PREDEFINED_HANDLERS

  def getAllForFrom(lib: Library): Iterable[ScalaLibraryMigrationHandler] = getAll filter (_.acceptsFrom(lib))

  def getAllForTo(lib: LibraryData): Iterable[ScalaLibraryMigrationHandler] = getAll filter (_.acceptsTo(lib))

}

object ArtifactHandlerService {
  def getInstance(project: Project): ArtifactHandlerService = project.getService(classOf[ArtifactHandlerService])
}