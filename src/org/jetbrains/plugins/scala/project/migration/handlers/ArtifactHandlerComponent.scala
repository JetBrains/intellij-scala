package org.jetbrains.plugins.scala.project.migration.handlers

import com.intellij.openapi.components.AbstractProjectComponent
import com.intellij.openapi.externalSystem.model.project.LibraryData
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.libraries.Library
import org.jetbrains.plugins.scala.project.migration.defaultimpl.ScalaStdLibHandler

/**
  * User: Dmitry.Naydanov
  * Date: 02.09.16.
  */
class ArtifactHandlerComponent(project: Project) extends AbstractProjectComponent(project) {
  private val PREDEFINED_HANDLERS = Seq[ScalaLibraryMigrationHandler](new ScalaStdLibHandler)

  override def getComponentName: String = "ScalaSbtArtifactHandlerComponent"

  def getAll: Iterable[ScalaLibraryMigrationHandler] = PREDEFINED_HANDLERS
  
  def getAllForFrom(lib: Library): Iterable[ScalaLibraryMigrationHandler] = getAll filter (_.acceptsFrom(lib))
  
  def getAllForTo(lib: LibraryData): Iterable[ScalaLibraryMigrationHandler] = getAll filter (_.acceptsTo(lib))
  
  private def loadHandlersFromLibs() {} //todo
}

object ArtifactHandlerComponent {
  def getInstance(project: Project): ArtifactHandlerComponent = project.getComponent(classOf[ArtifactHandlerComponent])
}