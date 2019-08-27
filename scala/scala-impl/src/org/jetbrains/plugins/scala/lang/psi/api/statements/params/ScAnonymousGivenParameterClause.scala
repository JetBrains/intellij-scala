package org.jetbrains.plugins.scala.lang.psi.api
package statements
package params

import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement

trait ScAnonymousGivenParameterClause extends ScParameterClause {
  def anonymousGivenTypeElements: Seq[ScTypeElement]
}