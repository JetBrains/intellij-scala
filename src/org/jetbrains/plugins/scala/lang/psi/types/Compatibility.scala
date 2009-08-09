package org.jetbrains.plugins.scala
package lang
package psi
package types

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