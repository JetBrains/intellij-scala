package org.jetbrains.plugins.scala.lang.psi.types
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement

trait ScType {

  def equiv(t: ScType): Boolean = false

  def conforms(t: ScType): Boolean = false
}