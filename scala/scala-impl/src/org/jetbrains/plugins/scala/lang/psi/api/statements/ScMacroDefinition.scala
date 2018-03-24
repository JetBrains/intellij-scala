package org.jetbrains.plugins.scala
package lang
package psi
package api
package statements

import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReferenceElement
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement

/**
 * @author Jason Zaugg
 */
trait ScMacroDefinition extends ScFunction {
  def typeElement: Option[ScTypeElement] = returnTypeElement

  def macroImplReference: Option[ScStableCodeReferenceElement]
}

object ScMacroDefinition {
  def isMacroImplReference(ref: ScStableCodeReferenceElement): Boolean =
    ref.getContext match {
      case m: ScMacroDefinition => m.macroImplReference.contains(ref)
      case _ => false
    }
}