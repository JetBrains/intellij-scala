package org.jetbrains.plugins.dotty.project

import com.intellij.openapi.roots.libraries.PersistentLibraryKind
import org.jetbrains.plugins.scala.project.{ScalaLibraryKind, ScalaLibraryProperties}

/**
  * @author adkozlov
  */
object DottyLibraryKind extends PersistentLibraryKind[ScalaLibraryProperties]("Dotty") with ScalaLibraryKind
