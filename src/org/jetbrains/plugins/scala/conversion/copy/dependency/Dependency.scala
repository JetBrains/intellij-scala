package org.jetbrains.plugins.scala.conversion.copy.dependency

/**
 * Pavel Fatin
 */

trait Dependency {
  def startOffset: Int

  def endOffset: Int
}