package org.jetbrains.plugins.scala.lang.psi.types
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement

trait ScType {

  def equiv(t: ScType): Boolean = false

  def conforms(t: ScType): Boolean = false
  
  def create (ref : ScReferenceElement) {
    val rr = ref.bind
    rr.element match {
      case null => null
      case td : ScTypeDefinition => new ScParameterizedType(td, rr.substitutor)
      case e => new ScDesignatorType(e)
    }
  }
}