package org.jetbrains.plugins.scala
package lang.dependency

/**
 * Pavel Fatin
 */

sealed trait DependencyKind

object DependencyKind {
  case object Reference extends DependencyKind

  case object Conversion extends DependencyKind
}