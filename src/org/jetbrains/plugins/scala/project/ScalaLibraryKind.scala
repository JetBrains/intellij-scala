package org.jetbrains.plugins.scala
package project

import com.intellij.openapi.roots.libraries.PersistentLibraryKind

/**
 * @author Pavel Fatin
 */
object ScalaLibraryKind extends PersistentLibraryKind[ScalaLibraryProperties]("Scala") with ScalaLibraryKind

trait ScalaLibraryKind extends PersistentLibraryKind[ScalaLibraryProperties] {
  override def createDefaultProperties() = new ScalaLibraryProperties()
}