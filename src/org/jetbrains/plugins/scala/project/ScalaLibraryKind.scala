package org.jetbrains.plugins.scala
package project

import com.intellij.openapi.roots.libraries.PersistentLibraryKind

/**
 * @author Pavel Fatin
 */
object ScalaLibraryKind extends PersistentLibraryKind[ScalaLibraryProperties]("Scala") {
  def createDefaultProperties() = new ScalaLibraryProperties()
}