package org.jetbrains.plugins.scala.lang.psi.api.statements

import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScEnum, ScTypeDefinition}

trait ScEnumCase extends ScTypeDefinition {
  def qualifiedName: String

  def enumParent: ScEnum

  def enumCases: ScEnumCases

  /**
   * Returns type parameters from an explicit type parameter clause only,
   * as opposed to [[typeParameters]], which will also return
   * type params implicitly inherited from parent enum class.
   */
  def physicalTypeParameters: Seq[ScTypeParam]
}
