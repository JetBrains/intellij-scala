package org.jetbrains.plugins.scala.lang.psi.types

/**
 * @author ven
 */
object Compatibility {
  def compatible(l : ScType, r : ScType) = {
    if (l conforms r) true
    else
      //todo check view applicability
    false
  }
}