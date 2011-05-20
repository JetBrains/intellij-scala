package org.jetbrains.plugins.scala.conversion.copy.dependency

/**
 * Pavel Fatin
 */

trait Dependency {
  def startOffset: Int

  def endOffset: Int

  def movedTo(startOffset: Int, endOffset: Int): Dependency

  def path(wildchardMembers: Boolean): String
}