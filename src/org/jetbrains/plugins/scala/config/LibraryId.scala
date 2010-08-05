package org.jetbrains.plugins.scala.config


/**
 * Pavel.Fatin, 04.08.2010
 */

object LibraryId {
  val empty = new LibraryId("", null)
} 

case class LibraryId(name: String, level: LibraryLevel) {
  def isEmpty = name.isEmpty || level == null
} 