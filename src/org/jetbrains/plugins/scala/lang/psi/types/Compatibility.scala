package org.jetbrains.plugins.scala.lang.psi.types

/**
 * @author ven
 */
object Compatibility {
  def compatible(l : ScType, r : ScType) = {
    if (r conforms l) {
      true
    }
    else
      //todo check view applicability
    false
  }
}