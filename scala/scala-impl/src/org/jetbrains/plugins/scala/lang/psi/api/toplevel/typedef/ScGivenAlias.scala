package org.jetbrains.plugins.scala
package lang
package psi
package api
package toplevel
package typedef

import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScDefinitionWithAssignment, ScFunction, ScFunctionDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement

trait ScGivenAlias extends ScGiven with ScFunctionDefinition {
  def typeElement: ScTypeElement
}
