package org.jetbrains.plugins.dotty.project

import com.intellij.openapi.roots.libraries.PersistentLibraryKind
import org.jetbrains.plugins.scala.project.{ScalaLanguageLevelProxy, ScalaLibraryPropertiesState, ScalaLibraryKind, ScalaLibraryProperties}

/**
  * @author adkozlov
  */
object DottyLibraryKind extends PersistentLibraryKind[ScalaLibraryProperties]("Dotty") with ScalaLibraryKind {
  override def createDefaultProperties(): ScalaLibraryProperties = {
    val props = new ScalaLibraryProperties()
    props.loadState(new ScalaLibraryPropertiesState(ScalaLanguageLevelProxy.Dotty))
    props
  }
}
