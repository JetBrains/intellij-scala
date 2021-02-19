package org.jetbrains.plugins.scala
package lang
package psi
package api
package statements

import org.jetbrains.plugins.scala.lang.psi.api._


import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReference
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement

/**
 * @author Jason Zaugg
 */
trait ScMacroDefinitionBase extends ScFunctionBase { this: ScMacroDefinition =>
  def typeElement: Option[ScTypeElement] = returnTypeElement

  def macroImplReference: Option[ScStableCodeReference]
}

abstract class ScMacroDefinitionCompanion {
  def isMacroImplReference(ref: ScStableCodeReference): Boolean =
    ref.getContext match {
      case m: ScMacroDefinition => m.macroImplReference.contains(ref)
      case _ => false
    }
}